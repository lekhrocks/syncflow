package com.syncflow.api.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.syncflow.api.metadata.MetadataDiscoveryService;
import com.syncflow.api.pipeline.entity.PipelineDesignEntity;
import com.syncflow.api.pipeline.entity.PipelineDesignVersionEntity;
import com.syncflow.api.pipeline.mapper.JsonMapper;
import com.syncflow.api.pipeline.mapper.PipelineDesignEntityMapper;
import com.syncflow.api.pipeline.mapper.PipelineDesignEntityMapperImpl;
import com.syncflow.api.pipeline.repository.PipelineDesignJpaRepository;
import com.syncflow.api.pipeline.repository.PipelineDesignVersionJpaRepository;
import com.syncflow.core.pipeline.DestinationReference;
import com.syncflow.core.pipeline.PipelineDesign;
import com.syncflow.core.pipeline.PipelineName;
import com.syncflow.core.pipeline.PipelineSettings;
import com.syncflow.core.pipeline.PipelineStatus;
import com.syncflow.core.pipeline.SourceReference;
import com.syncflow.core.pipeline.validation.ValidationResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PipelineDesignerService")
class PipelineDesignerServiceTest {

    @Mock
    private PipelineDesignJpaRepository designRepo;
    @Mock
    private PipelineDesignVersionJpaRepository versionRepo;
    @Mock
    private PipelineValidator validator;
    @Mock
    private MetadataDiscoveryService metadataService;

    private PipelineDesignerService service;
    private JsonMapper jsonMapper;
    private PipelineDesignEntityMapper mapper;

    @BeforeEach
    void setUp() {
        var objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        jsonMapper = new JsonMapper(objectMapper);
        mapper = new PipelineDesignEntityMapperImpl();
        service = new PipelineDesignerService(designRepo, versionRepo, mapper, jsonMapper,
                validator, metadataService, new SimpleMeterRegistry());
    }

    // ── Test data helpers ────────────────────────────────────────────────────

    private PipelineName name(String value) {
        return new PipelineName(value);
    }

    private SourceReference source() {
        return new SourceReference("conn-src", "public", "users");
    }

    private DestinationReference dest() {
        return new DestinationReference("conn-dst", "public", "users_copy", "UPSERT");
    }

    private PipelineSettings settings() {
        return PipelineSettings.defaults();
    }

    private PipelineDesignEntity entityFor(PipelineDesign design) {
        return mapper.toEntity(design, jsonMapper);
    }

    // ── create ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        void returnsDesignWithDraftStatus() {
            var design = service.create(name("my-pipeline"), source(), dest(), List.of(), settings());
            assertEquals(PipelineStatus.DRAFT, design.status());
        }

        @Test
        void returnsDesignWithCorrectName() {
            var design = service.create(name("my-pipeline"), source(), dest(), List.of(), settings());
            assertEquals("my-pipeline", design.name().value());
        }

        @Test
        void savesEntityToRepo() {
            service.create(name("my-pipeline"), source(), dest(), List.of(), settings());
            verify(designRepo, times(1)).save(any(PipelineDesignEntity.class));
        }

        @Test
        void savesInitialVersion() {
            service.create(name("my-pipeline"), source(), dest(), List.of(), settings());
            verify(versionRepo, times(1)).save(any(PipelineDesignVersionEntity.class));
        }

        @Test
        void initialVersionIsOne() {
            var design = service.create(name("v1-pipeline"), source(), dest(), List.of(), settings());
            assertEquals(1, design.audit().version());
        }

        @Test
        void setsCreatedByToSystem() {
            var design = service.create(name("sys-pipeline"), source(), dest(), List.of(), settings());
            assertEquals("system", design.audit().createdBy());
        }

        @Test
        void generatesNonNullId() {
            var design = service.create(name("id-pipeline"), source(), dest(), List.of(), settings());
            assertNotNull(design.id());
            assertNotNull(design.id().value());
        }

        @Test
        void versionSnapshotHasCorrectPipelineId() {
            var captor = ArgumentCaptor.forClass(PipelineDesignVersionEntity.class);
            var design = service.create(name("snap-pipeline"), source(), dest(), List.of(), settings());
            verify(versionRepo).save(captor.capture());
            assertEquals(1, captor.getValue().getVersion());
        }
    }

    // ── get ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("get()")
    class Get {

        @Test
        void returnsDesignWhenFound() {
            var design = PipelineDesign.create(name("found"), source(), dest(), List.of(), settings());
            when(designRepo.findByIdAndTenantId(eq(design.id().value()), anyString()))
                    .thenReturn(Optional.of(entityFor(design)));
            var result = service.get(design.id().value());
            assertEquals("found", result.name().value());
        }

        @Test
        void throwsNoSuchElementWhenNotFound() {
            when(designRepo.findByIdAndTenantId(eq("missing-id"), anyString())).thenReturn(Optional.empty());
            assertThrows(NoSuchElementException.class, () -> service.get("missing-id"));
        }

        @Test
        void errorMessageContainsPipelineId() {
            when(designRepo.findByIdAndTenantId(eq("abc"), anyString())).thenReturn(Optional.empty());
            var ex = assertThrows(NoSuchElementException.class, () -> service.get("abc"));
            assert ex.getMessage().contains("abc");
        }
    }

    // ── list ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("list()")
    class ListAll {

        @Test
        void returnsEmptyListWhenNoDesigns() {
            when(designRepo.findByTenantId(anyString())).thenReturn(List.of());
            assertEquals(0, service.list().size());
        }

        @Test
        void returnsAllDesigns() {
            var d1 = PipelineDesign.create(name("p1"), source(), dest(), List.of(), settings());
            var d2 = PipelineDesign.create(name("p2"), source(), dest(), List.of(), settings());
            when(designRepo.findByTenantId(anyString())).thenReturn(List.of(entityFor(d1), entityFor(d2)));
            assertEquals(2, service.list().size());
        }
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        void incrementsVersion() {
            var design = PipelineDesign.create(name("original"), source(), dest(), List.of(), settings());
            var entity = entityFor(design);
            when(designRepo.findByIdAndTenantId(eq(design.id().value()), anyString())).thenReturn(Optional.of(entity));
            var updated = service.update(design.id().value(), name("renamed"),
                    source(), dest(), List.of(), settings());
            assertEquals(2, updated.audit().version());
        }

        @Test
        void updatesName() {
            var design = PipelineDesign.create(name("old-name"), source(), dest(), List.of(), settings());
            when(designRepo.findByIdAndTenantId(eq(design.id().value()), anyString()))
                    .thenReturn(Optional.of(entityFor(design)));
            var updated = service.update(design.id().value(), name("new-name"),
                    source(), dest(), List.of(), settings());
            assertEquals("new-name", updated.name().value());
        }

        @Test
        void savesEntityAndVersionOnUpdate() {
            var design = PipelineDesign.create(name("upd"), source(), dest(), List.of(), settings());
            when(designRepo.findByIdAndTenantId(eq(design.id().value()), anyString()))
                    .thenReturn(Optional.of(entityFor(design)));
            service.update(design.id().value(), name("upd2"), source(), dest(), List.of(), settings());
            verify(designRepo).save(any(PipelineDesignEntity.class));
            verify(versionRepo).save(any(PipelineDesignVersionEntity.class));
        }

        @Test
        void throwsWhenPipelineNotFound() {
            when(designRepo.findByIdAndTenantId(eq("x"), anyString())).thenReturn(Optional.empty());
            assertThrows(NoSuchElementException.class,
                    () -> service.update("x", name("n"), source(), dest(), List.of(), settings()));
        }
    }

    // ── delete ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        void deletesByIdWhenExists() {
            when(designRepo.findByIdAndTenantId(eq("del-id"), anyString()))
                    .thenReturn(Optional.of(entityFor(PipelineDesign.create(
                            name("del"), source(), dest(), List.of(), settings()))));
            service.delete("del-id");
            verify(designRepo).delete(any(PipelineDesignEntity.class));
        }

        @Test
        void throwsWhenNotFound() {
            when(designRepo.findByIdAndTenantId(eq("gone"), anyString())).thenReturn(Optional.empty());
            assertThrows(NoSuchElementException.class, () -> service.delete("gone"));
            verify(designRepo, never()).delete(any());
        }
    }

    // ── validate ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validate()")
    class Validate {

        @Test
        void delegatesToValidator() {
            var design = PipelineDesign.create(name("val"), source(), dest(), List.of(), settings());
            when(designRepo.findByIdAndTenantId(eq(design.id().value()), anyString()))
                    .thenReturn(Optional.of(entityFor(design)));
            when(validator.validate(any(PipelineDesign.class)))
                    .thenReturn(new ValidationResult(true, List.of()));
            var result = service.validate(design.id().value());
            verify(validator, times(1)).validate(any(PipelineDesign.class));
            assert result.valid();
        }
    }

    // ── versions ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("versions()")
    class Versions {

        @Test
        void returnsEmptyListWhenNoVersions() {
            when(designRepo.findByIdAndTenantId(eq("pid"), anyString()))
                    .thenReturn(Optional.of(entityFor(PipelineDesign.create(
                            name("pid"), source(), dest(), List.of(), settings()))));
            when(versionRepo.findByPipelineIdOrderByVersionAsc("pid")).thenReturn(List.of());
            assertEquals(0, service.versions("pid").size());
        }

        @Test
        void deserializesVersionSnapshots() {
            var design = PipelineDesign.create(name("versioned"), source(), dest(), List.of(), settings());
            var vEntity = new PipelineDesignVersionEntity();
            vEntity.setVersion(1);
            vEntity.setSnapshot(jsonMapper.toJson(design));
            vEntity.setSavedAt(Instant.now());
            when(designRepo.findByIdAndTenantId(eq(design.id().value()), anyString()))
                    .thenReturn(Optional.of(entityFor(design)));
            when(versionRepo.findByPipelineIdOrderByVersionAsc(design.id().value()))
                    .thenReturn(List.of(vEntity));
            var versions = service.versions(design.id().value());
            assertEquals(1, versions.size());
            assertEquals("versioned", versions.get(0).name().value());
        }
    }

    // ── rollback ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("rollback()")
    class Rollback {

        @Test
        void restoresDesignFromVersion() {
            var design = PipelineDesign.create(name("rollback-me"), source(), dest(), List.of(), settings());
            var entity = entityFor(design);

            var vEntity = new PipelineDesignVersionEntity();
            vEntity.setVersion(1);
            vEntity.setSnapshot(jsonMapper.toJson(design));
            vEntity.setSavedAt(Instant.now());
            vEntity.setPipeline(entity);

            when(versionRepo.findByPipelineIdAndVersion(design.id().value(), 1))
                    .thenReturn(Optional.of(vEntity));
            when(designRepo.findByIdAndTenantId(eq(design.id().value()), anyString())).thenReturn(Optional.of(entity));

            var rolled = service.rollback(design.id().value(), 1);
            assertEquals("rollback-me", rolled.name().value());
        }

        @Test
        void throwsWhenVersionNotFound() {
            when(designRepo.findByIdAndTenantId(eq("pid"), anyString()))
                    .thenReturn(Optional.of(entityFor(PipelineDesign.create(
                            name("pid"), source(), dest(), List.of(), settings()))));
            when(versionRepo.findByPipelineIdAndVersion("pid", 99))
                    .thenReturn(Optional.empty());
            assertThrows(NoSuchElementException.class, () -> service.rollback("pid", 99));
        }

        @Test
        void savesEntityAfterRollback() {
            var design = PipelineDesign.create(name("rb"), source(), dest(), List.of(), settings());
            var entity = entityFor(design);
            var vEntity = new PipelineDesignVersionEntity();
            vEntity.setVersion(1);
            vEntity.setSnapshot(jsonMapper.toJson(design));
            vEntity.setSavedAt(Instant.now());
            vEntity.setPipeline(entity);

            when(versionRepo.findByPipelineIdAndVersion(design.id().value(), 1))
                    .thenReturn(Optional.of(vEntity));
            when(designRepo.findByIdAndTenantId(eq(design.id().value()), anyString())).thenReturn(Optional.of(entity));

            service.rollback(design.id().value(), 1);
            verify(designRepo).save(any(PipelineDesignEntity.class));
        }
    }
}
