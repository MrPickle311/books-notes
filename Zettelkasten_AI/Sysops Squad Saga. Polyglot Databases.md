
The team decides the `Survey` data domain is a good candidate for migrating from a relational to a document database. After getting business justification (the relational model was slow to change and hindered marketing's agility), they analyze two data modeling options.

1.  **Single Aggregate:** Store the survey and all its questions in a single JSON document.
    *   **Pro:** Very easy for the UI to retrieve and render.
    *   **Con:** Question data is duplicated across every survey document.
    ```json
    // Example 6-3: JSON for a single aggregate survey document.
    {
        "survey_id": "19999",
        "description": "Survey to gauge customer...",
        "questions": [
            { "question_id": "50001", "question": "Rate the expert", ... },
            { "question_id": "50000", "question": "Did the expert fix the problem?", ... }
        ]
    }
    ```

2.  **Split Aggregate:** Store surveys and questions in separate documents, with the survey referencing question IDs.
    *   **Pro:** Questions are not duplicated and can be reused.
    *   **Con:** Requires the UI to make multiple database calls (one for the survey, then one for each question) to render the full survey.
    ```json
    // Example 6-4: JSON for a split aggregate design.
    // Survey aggregate
    {
        "survey_id": "19999",
        "description": "Survey to gauge customer...",
        "questions": [
            {"question_id": "50001", "order": "2"},
            {"question_id": "50000", "order": "1"}
        ]
    }
    // Question aggregates
    { "question_id": "50001", "question": "Rate the expert", ... }
    { "question_id": "50000", "question": "Did the expert fix the problem?", ... }
    ```

The team decides to go with the **single aggregate** model, trading off data duplication for improved UI performance and ease of change, and they document this decision in an ADR.