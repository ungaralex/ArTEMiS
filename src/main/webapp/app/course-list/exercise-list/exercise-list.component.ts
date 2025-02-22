import { Component, Input, OnDestroy, OnInit, Pipe, PipeTransform } from '@angular/core';
import { Course, CourseExerciseService } from '../../entities/course';
import { Exercise, ExerciseType, ParticipationStatus } from '../../entities/exercise';
import { AccountService } from '../../core';
import { WindowRef } from '../../core/websocket/window.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiAlertService } from 'ng-jhipster';
import { NavigationStart, Router } from '@angular/router';
import { InitializationState, Participation, ParticipationService } from '../../entities/participation';
import { ParticipationDataProvider } from './/participation-data-provider';
import { HttpClient } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { QuizExercise } from '../../entities/quiz-exercise';
import * as moment from 'moment';
import { SourceTreeService } from 'app/components/util/sourceTree.service';
import { DiagramType, ModelingExercise } from 'app/entities/modeling-exercise';

@Pipe({ name: 'showExercise' })
export class ShowExercisePipe implements PipeTransform {
    transform(allExercises: Exercise[], showInactiveExercises: boolean) {
        return allExercises.filter(exercise => showInactiveExercises === true || exercise.type === ExerciseType.QUIZ || !exercise.dueDate || exercise.dueDate > moment());
    }
}

@Component({
    selector: 'jhi-exercise-list',
    templateUrl: './exercise-list.component.html',
    providers: [JhiAlertService, WindowRef, ParticipationService, CourseExerciseService, NgbModal, SourceTreeService],
})
export class ExerciseListComponent implements OnInit, OnDestroy {
    // Make constants available to html for comparison
    readonly QUIZ = ExerciseType.QUIZ;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly MODELING = ExerciseType.MODELING;
    readonly TEXT = ExerciseType.TEXT;
    readonly CLASS_DIAGRAM = DiagramType.ClassDiagram;

    _course: Course;
    routerSubscription: Subscription;

    @Input()
    get course(): Course {
        return this._course;
    }
    set course(course: Course) {
        this._course = course;
        // exercises already included in data, no need to load them
        this.initExercises(course.exercises);
    }
    @Input()
    filterByExerciseId: number;

    /*
     * IMPORTANT NOTICE:
     * The Angular team and many experienced Angular developers strongly recommend that you move filtering and sorting
     * logic into the component itself. [...] Any capabilities that you would have put in a pipe and shared across
     * the app can be written in a filtering/sorting service and injected into the component.
     */

    // Exercises are sorted by dueDate
    exercises: Exercise[];
    now = Date.now();
    numOfInactiveExercises = 0;
    showInactiveExercises = false;
    repositoryPassword: string;
    wasCopied = false;

    constructor(
        private jhiAlertService: JhiAlertService,
        private $window: WindowRef,
        private participationService: ParticipationService,
        private accountService: AccountService,
        private httpClient: HttpClient,
        private courseExerciseService: CourseExerciseService,
        private sourceTreeService: SourceTreeService,
        private router: Router,
        private participationDataProvider: ParticipationDataProvider,
    ) {
        // Initialize array to avoid undefined errors
        this.exercises = [];
    }

    asModelingExercise(exercise: Exercise): ModelingExercise {
        return exercise as ModelingExercise;
    }

    ngOnInit(): void {
        this.accountService.identity().then(user => {
            // Only load password if current user login starts with 'edx_' or 'u4i_'
            if (user && user.login && (user.login.startsWith('edx_') || user.login.startsWith('u4i_'))) {
                this.getRepositoryPassword();
            }
        });
        // Listen to NavigationStart events; if we are routing to the online editor, we pass the participation (if we find one)
        this.routerSubscription = this.router.events
            .filter(event => event instanceof NavigationStart)
            .subscribe((event: NavigationStart) => {
                if (event.url.startsWith('/code-editor')) {
                    // Extract participation id from event url and cast to number
                    const participationId = Number(event.url.split('/').slice(-1));
                    // Search through all exercises and the participations within each of them to obtain the target participation
                    const filteredExercise = this.course.exercises.find(
                        exercise =>
                            exercise.participations != null && exercise.participations.find(exerciseParticipation => exerciseParticipation.id === participationId) !== undefined,
                    );
                    if (filteredExercise) {
                        const participation: Participation = filteredExercise.participations.find(currentParticipation => currentParticipation.id === participationId);
                        // Just make sure we have indeed found the desired participation
                        if (participation && participation.id === participationId) {
                            this.participationDataProvider.participationStorage = participation;
                        }
                    }
                }
            });
    }

    initExercises(exercises: Exercise[]) {
        if (this.filterByExerciseId) {
            exercises = exercises.filter(exercise => exercise.id === this.filterByExerciseId);
        }

        this.numOfInactiveExercises = exercises.filter(exercise => !this.showExercise(exercise)).length;

        for (const exercise of exercises) {
            // We assume that exercise has a participation and a result if available because of the explicit courses dashboard call
            exercise.course = this._course;
            exercise.participationStatus = this.participationStatus(exercise);

            if (this.hasParticipations(exercise)) {
                // Reconnect 'participation --> exercise' in case it is needed
                exercise.participations[0].exercise = exercise;
            }

            // If the User is a student: subscribe the release Websocket of every quizExercise
            if (exercise.type === ExerciseType.QUIZ) {
                const quizExercise = exercise as QuizExercise;
                quizExercise.isActiveQuiz = this.isActiveQuiz(exercise);

                quizExercise.isPracticeModeAvailable = quizExercise.isPlannedToStart && quizExercise.isOpenForPractice && moment(exercise.dueDate).isBefore(moment());
            }

            exercise.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(exercise.course);
            exercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(exercise.course);
        }
        exercises.sort((a: Exercise, b: Exercise) => {
            if (a.dueDate === null && b.dueDate === null) {
                return 0;
            } else if (a.dueDate === null) {
                return +1;
            } else if (b.dueDate === null) {
                return -1;
            } else {
                return +a.dueDate.toDate() - +b.dueDate.toDate();
            }
        });
        this.exercises = exercises;
    }

    isActiveQuiz(exercise: Exercise) {
        return (
            exercise.participationStatus === ParticipationStatus.QUIZ_UNINITIALIZED ||
            exercise.participationStatus === ParticipationStatus.QUIZ_ACTIVE ||
            exercise.participationStatus === ParticipationStatus.QUIZ_SUBMITTED
        );
    }

    showExercise(exercise: Exercise) {
        return this.showInactiveExercises === true || exercise.type === ExerciseType.QUIZ || !exercise.dueDate || exercise.dueDate > moment();
    }

    getRepositoryPassword() {
        this.sourceTreeService.getRepositoryPassword().subscribe(res => {
            const password = res['password'];
            if (password) {
                this.repositoryPassword = password;
            }
        });
    }

    startExercise(exercise: Exercise) {
        exercise.loading = true;

        if (exercise.type === ExerciseType.QUIZ) {
            // Start the quiz
            return this.router.navigate(['/quiz', exercise.id]);
        }

        this.courseExerciseService
            .startExercise(this.course.id, exercise.id)
            .finally(() => (exercise.loading = false))
            .subscribe(
                participation => {
                    if (participation) {
                        exercise.participations = [participation];
                        exercise.participationStatus = this.participationStatus(exercise);
                    }
                    if (exercise.type === ExerciseType.PROGRAMMING) {
                        this.jhiAlertService.success('arTeMiSApp.exercise.personalRepository');
                    }
                },
                error => {
                    console.log('Error: ' + error);
                    this.jhiAlertService.warning('arTeMiSApp.exercise.startError');
                },
            );
    }

    resumeExercise(exercise: Exercise) {
        exercise.loading = true;
        this.courseExerciseService
            .resumeExercise(this.course.id, exercise.id)
            .finally(() => (exercise.loading = false))
            .subscribe(
                participation => {
                    if (participation) {
                        exercise.participations = [participation];
                        exercise.participationStatus = this.participationStatus(exercise);
                    }
                    if (exercise.type === ExerciseType.PROGRAMMING) {
                        this.jhiAlertService.success('arTeMiSApp.exercise.personalRepository');
                    }
                },
                error => {
                    console.log('Error: ' + error);
                    this.jhiAlertService.warning('arTeMiSApp.exercise.startError');
                },
            );
    }

    startPractice(exercise: Exercise) {
        return this.router.navigate(['/quiz', exercise.id, 'practice']);
    }

    toggleshowInactiveExercises() {
        this.showInactiveExercises = !this.showInactiveExercises;
    }

    buildSourceTreeUrl(cloneUrl: string): string {
        return this.sourceTreeService.buildSourceTreeUrl(cloneUrl);
    }

    goToBuildPlan(participation: Participation) {
        this.sourceTreeService.goToBuildPlan(participation);
    }

    onCopyFailure() {
        console.log('copy fail!');
    }

    onCopySuccess() {
        this.wasCopied = true;
        setTimeout(() => {
            this.wasCopied = false;
        }, 3000);
    }

    participationStatus(exercise: Exercise): ParticipationStatus {
        if (exercise.type === ExerciseType.QUIZ) {
            const quizExercise = exercise as QuizExercise;
            if ((!quizExercise.isPlannedToStart || moment(quizExercise.releaseDate).isAfter(moment())) && quizExercise.visibleToStudents) {
                return ParticipationStatus.QUIZ_NOT_STARTED;
            } else if (!this.hasParticipations(exercise) && (!quizExercise.isPlannedToStart || moment(quizExercise.dueDate).isAfter(moment())) && quizExercise.visibleToStudents) {
                return ParticipationStatus.QUIZ_UNINITIALIZED;
            } else if (!this.hasParticipations(exercise)) {
                return ParticipationStatus.QUIZ_NOT_PARTICIPATED;
            } else if (exercise.participations[0].initializationState === InitializationState.INITIALIZED && moment(exercise.dueDate).isAfter(moment())) {
                return ParticipationStatus.QUIZ_ACTIVE;
            } else if (exercise.participations[0].initializationState === InitializationState.FINISHED && moment(exercise.dueDate).isAfter(moment())) {
                return ParticipationStatus.QUIZ_SUBMITTED;
            } else {
                if (!this.hasResults(exercise.participations[0])) {
                    return ParticipationStatus.QUIZ_NOT_PARTICIPATED;
                }
                return ParticipationStatus.QUIZ_FINISHED;
            }
        } else if ((exercise.type === ExerciseType.MODELING || exercise.type === ExerciseType.TEXT) && this.hasParticipations(exercise)) {
            const participation = exercise.participations[0];
            if (participation.initializationState === InitializationState.INITIALIZED || participation.initializationState === InitializationState.FINISHED) {
                return exercise.type === ExerciseType.MODELING ? ParticipationStatus.MODELING_EXERCISE : ParticipationStatus.TEXT_EXERCISE;
            }
        }

        if (!this.hasParticipations(exercise)) {
            return ParticipationStatus.UNINITIALIZED;
        } else if (exercise.participations[0].initializationState === InitializationState.INITIALIZED) {
            return ParticipationStatus.INITIALIZED;
        }
        return ParticipationStatus.INACTIVE;
    }

    hasParticipations(exercise: Exercise): boolean {
        return exercise.participations && exercise.participations.length > 0;
    }

    hasResults(participation: Participation): boolean {
        return participation.results && participation.results.length > 0;
    }

    ngOnDestroy(): void {
        // Remove router event subscription
        this.routerSubscription.unsubscribe();
    }
}
