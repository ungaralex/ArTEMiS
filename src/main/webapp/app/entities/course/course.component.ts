import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { Course } from './course.model';
import { CourseService } from './course.service';
import * as moment from 'moment';

@Component({
    selector: 'jhi-course',
    templateUrl: './course.component.html',
    styles: ['.course-table {padding-bottom: 5rem}'],
})
export class CourseComponent implements OnInit, OnDestroy {
    predicate: string;
    reverse: boolean;
    showOnlyActive = false;

    courses: Course[];
    eventSubscriber: Subscription;

    constructor(private courseService: CourseService, private jhiAlertService: JhiAlertService, private eventManager: JhiEventManager) {
        this.predicate = 'id';
        // show the newest courses first and the oldest last
        this.reverse = false;
    }

    loadAll() {
        this.courseService.query().subscribe(
            (res: HttpResponse<Course[]>) => {
                this.courses = res.body!;
            },
            (res: HttpErrorResponse) => this.onError(res),
        );
    }

    ngOnInit() {
        this.loadAll();
        this.registerChangeInCourses();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    get today() {
        return moment(new Date());
    }

    trackId(index: number, item: Course) {
        return item.id;
    }

    registerChangeInCourses() {
        this.eventSubscriber = this.eventManager.subscribe('courseListModification', () => this.loadAll());
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
    }

    callback() {}
}
