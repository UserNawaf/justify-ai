# Extra templates

Every `*.yml` file in this folder is loaded at start-up (`TEMPLATES_DIR`, mounted at `/templates` in Docker
Compose). A file with the same `id` as a bundled template overrides it. No code changes needed.

```yaml
id: change-request            # lowercase, digits, dashes
name: Change request           # shown as "Request type" in the result
name_ar: طلب تغيير               # optional, shown in the Arabic UI
description: Request to change a production system.
fields:                        # order = order in the result
  - key: system                # lowercase, digits, underscores
    label: System
    required: true             # required fields are asked for when missing
    description: Which system or application. Shown to the model.
    question: Which system will be changed?
    label_ar: النظام                # optional
    question_ar: أي نظام بيتغير؟     # optional, falls back to English
  - key: rollback
    label: Rollback plan
    required: true
    question: How do we roll back if the change fails?
```

Bundled templates (`backend/src/main/resources/templates/`) are a good reference. Restart the backend after adding a file. Invalid files stop the start-up with a clear error.
