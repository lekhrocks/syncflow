name: Bug fix or feature
description: Submit a pull request to SyncFlow
title: ''
labels: ''
assignees: ''
body:
  - type: markdown
    attributes:
      value: |
        ## Description
        Please include a summary of the change and which issue is fixed.

  - type: textarea
    id: description
    attributes:
      label: Description
      description: What does this PR change?
    validations:
      required: true

  - type: checkboxes
    id: checklist
    attributes:
      label: PR Checklist
      options:
        - label: ./gradlew spotlessCheck passes
          required: true
        - label: ./gradlew test passes
          required: true
        - label: New endpoints have REST contract tests
          required: false
        - label: New domain objects have unit tests
          required: false
        - label: CHANGELOG.md updated with Added or Fixed entry
          required: true
        - label: Migration strategy documented if database schema changes
          required: false
        - label: Related issue linked
          required: false
