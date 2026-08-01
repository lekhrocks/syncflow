package com.syncflow.connector.validator;

import com.syncflow.core.connection.ConnectionProperties;
import com.syncflow.core.connection.ConnectionType;
import org.springframework.stereotype.Component;

@Component
public class MySqlValidator extends AbstractConnectorValidator {

    public MySqlValidator() {
        super(ConnectionType.MYSQL);
    }

    @Override
    protected String jdbcUrl(ConnectionProperties props) {
        var url = new StringBuilder("jdbc:mysql://");
        url.append(props.host()).append(':').append(props.port());
        url.append('/').append(props.database());
        if (!props.options().isEmpty()) {
            url.append('?');
            props.options().forEach((k, v) -> url.append(k).append('=').append(v).append('&'));
            url.setLength(url.length() - 1);
        }
        return url.toString();
    }
}
