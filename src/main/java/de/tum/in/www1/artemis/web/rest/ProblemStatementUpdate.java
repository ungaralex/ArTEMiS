package de.tum.in.www1.artemis.web.rest;

public class ProblemStatementUpdate {
    private Long exerciseId;
    private String problemStatement;

    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public String getProblemStatement() {
        return problemStatement;
    }

    public void setProblemStatement(String problemStatement) {
        this.problemStatement = problemStatement;
    }
}
