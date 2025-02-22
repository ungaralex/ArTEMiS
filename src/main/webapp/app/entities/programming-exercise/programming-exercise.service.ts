import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { omit as _omit } from 'lodash';
import { SERVER_API_URL } from 'app/app.constants';

import { ProgrammingExercise } from './programming-exercise.model';
import { createRequestOption } from 'app/shared';
import { ExerciseService } from 'app/entities/exercise';
import { Participation } from 'app/entities/participation';

export type EntityResponseType = HttpResponse<ProgrammingExercise>;
export type EntityArrayResponseType = HttpResponse<ProgrammingExercise[]>;

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseService {
    public resourceUrl = SERVER_API_URL + 'api/programming-exercises';

    constructor(private http: HttpClient, private exerciseService: ExerciseService) {}

    create(programmingExercise: ProgrammingExercise): Observable<EntityResponseType> {
        const copy = this.convertDataFromClient(programmingExercise);
        return this.http
            .post<ProgrammingExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res));
    }

    automaticSetup(programmingExercise: ProgrammingExercise): Observable<EntityResponseType> {
        const copy = this.convertDataFromClient(programmingExercise);
        return this.http
            .post<ProgrammingExercise>(this.resourceUrl + '/setup', copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res));
    }

    generateStructureOracle(exerciseId: number) {
        return this.http.get(this.resourceUrl + '/' + exerciseId + '/generate-tests', { responseType: 'text' });
    }

    update(programmingExercise: ProgrammingExercise): Observable<EntityResponseType> {
        const copy = this.convertDataFromClient(programmingExercise);
        return this.http
            .put<ProgrammingExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<ProgrammingExercise>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res));
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<ProgrammingExercise[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: EntityArrayResponseType) => this.exerciseService.convertDateArrayFromServer(res));
    }

    delete(id: number, deleteStudentReposBuildPlans: boolean, deleteBaseReposBuildPlans: boolean): Observable<HttpResponse<void>> {
        let params = new HttpParams();
        params = params.set('deleteStudentReposBuildPlans', deleteStudentReposBuildPlans.toString());
        params = params.set('deleteBaseReposBuildPlans', deleteBaseReposBuildPlans.toString());
        return this.http.delete<void>(`${this.resourceUrl}/${id}`, { params, observe: 'response' });
    }

    convertDataFromClient(exercise: ProgrammingExercise) {
        const copy = this.exerciseService.convertDateFromClient(exercise);
        // Remove exercise from template & solution participation to avoid circular dependency issues
        if (copy.templateParticipation) {
            copy.templateParticipation = _omit(copy.templateParticipation, 'exercise') as Participation;
        }
        if (copy.solutionParticipation) {
            copy.solutionParticipation = _omit(copy.solutionParticipation, 'exercise') as Participation;
        }

        return copy;
    }
}
