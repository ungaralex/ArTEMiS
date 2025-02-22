import { Component, EventEmitter, Input, Output } from '@angular/core';
import { compose, map, sortBy } from 'lodash/fp';
import { Participation } from 'app/entities/participation';
import { ProgrammingExercise } from '../programming-exercise.model';
import { Result } from 'app/entities/result';
import { DomainCommand } from 'app/markdown-editor/domainCommands';
import { TaskCommand } from 'app/markdown-editor/domainCommands/programming-exercise/task.command';
import { TestCaseCommand } from 'app/markdown-editor/domainCommands/programming-exercise/testCase.command';

@Component({
    selector: 'jhi-programming-exercise-editable-instructions',
    templateUrl: './programming-exercise-editable-instruction.component.html',
})
export class ProgrammingExerciseEditableInstructionComponent {
    participationValue: Participation;
    exerciseValue: ProgrammingExercise;

    exerciseTestCases: string[] = [];

    taskCommand = new TaskCommand();
    taskRegex = this.taskCommand.getTagRegex('g');
    testCaseCommand = new TestCaseCommand();
    domainCommands: DomainCommand[] = [this.taskCommand, this.testCaseCommand];

    @Input()
    get participation() {
        return this.participationValue;
    }
    @Output()
    participationChange = new EventEmitter<Participation>();

    set participation(participation: Participation) {
        this.participationValue = participation;
        this.participationChange.emit(this.participationValue);
    }

    @Input()
    get exercise() {
        return this.exerciseValue;
    }
    @Output()
    exerciseChange = new EventEmitter<ProgrammingExercise>();

    set exercise(exercise: ProgrammingExercise) {
        this.exerciseValue = exercise;
        this.exerciseChange.emit(this.exerciseValue);
    }

    @Output()
    onProblemStatementChange = new EventEmitter<string>();

    updateProblemStatement(problemStatement: string) {
        this.onProblemStatementChange.emit(problemStatement);
    }

    setTestCasesFromResults(result: Result) {
        // If the exercise is created, there is no result available
        this.exerciseTestCases =
            result && result.feedbacks
                ? compose(
                      map(({ text }) => text),
                      sortBy('text'),
                  )(result.feedbacks)
                : [];
        this.testCaseCommand.setValues(this.exerciseTestCases);
    }
}
