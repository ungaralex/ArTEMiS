package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.shortNamePattern;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static java.time.ZonedDateTime.now;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.TutorParticipationStatus;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.exception.ArtemisAuthenticationException;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ComplaintResponseRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProvider;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.dto.StatsForInstructorDashboardDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.config.JHipsterConstants;
import io.github.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing Course.
 */
@RestController
@RequestMapping({ "/api", "/api_basic" })
@PreAuthorize("hasRole('ADMIN')")
public class CourseResource {

    private final Logger log = LoggerFactory.getLogger(CourseResource.class);

    private static final String ENTITY_NAME = "course";

    private final Environment env;

    private final UserService userService;

    private final CourseService courseService;

    private final ParticipationService participationService;

    private final AuthorizationCheckService authCheckService;

    private final CourseRepository courseRepository;

    private final ExerciseService exerciseService;

    private final Optional<ArtemisAuthenticationProvider> artemisAuthenticationProvider;

    private final TutorParticipationService tutorParticipationService;

    private final ObjectMapper objectMapper;

    private final TextAssessmentService textAssessmentService;

    private final LectureService lectureService;

    private final ComplaintRepository complaintRepository;

    private final ComplaintResponseRepository complaintResponseRepository;

    private final NotificationService notificationService;

    private final TextSubmissionService textSubmissionService;

    private final ModelingSubmissionService modelingSubmissionService;

    private final ResultService resultService;

    private final ComplaintService complaintService;

    public CourseResource(Environment env, UserService userService, CourseService courseService, ParticipationService participationService, CourseRepository courseRepository,
            ExerciseService exerciseService, AuthorizationCheckService authCheckService, TutorParticipationService tutorParticipationService,
            MappingJackson2HttpMessageConverter springMvcJacksonConverter, Optional<ArtemisAuthenticationProvider> artemisAuthenticationProvider,
            TextAssessmentService textAssessmentService, ComplaintRepository complaintRepository, ComplaintResponseRepository complaintResponseRepository,
            LectureService lectureService, NotificationService notificationService, TextSubmissionService textSubmissionService,
            ModelingSubmissionService modelingSubmissionService, ResultService resultService, ComplaintService complaintService) {
        this.env = env;
        this.userService = userService;
        this.courseService = courseService;
        this.participationService = participationService;
        this.courseRepository = courseRepository;
        this.exerciseService = exerciseService;
        this.authCheckService = authCheckService;
        this.tutorParticipationService = tutorParticipationService;
        this.artemisAuthenticationProvider = artemisAuthenticationProvider;
        this.objectMapper = springMvcJacksonConverter.getObjectMapper();
        this.textAssessmentService = textAssessmentService;
        this.complaintRepository = complaintRepository;
        this.complaintResponseRepository = complaintResponseRepository;
        this.lectureService = lectureService;
        this.notificationService = notificationService;
        this.textSubmissionService = textSubmissionService;
        this.modelingSubmissionService = modelingSubmissionService;
        this.resultService = resultService;
        this.complaintService = complaintService;
    }

    /**
     * POST /courses : Create a new course.
     *
     * @param course the course to create
     * @return the ResponseEntity with status 201 (Created) and with body the new course, or with status 400 (Bad Request) if the course has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/courses")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Course> createCourse(@RequestBody Course course) throws URISyntaxException {
        log.debug("REST request to save Course : {}", course);
        if (course.getId() != null) {
            throw new BadRequestAlertException("A new course cannot already have an ID", ENTITY_NAME, "idexists");
        }
        try {
            // Check if course shortname matches regex
            Matcher shortNameMatcher = shortNamePattern.matcher(course.getShortName());
            if (!shortNameMatcher.matches()) {
                return ResponseEntity.badRequest().headers(HeaderUtil.createAlert("The shortname is invalid", "shortnameInvalid")).body(null);
            }
            checkIfGroupsExists(course);
            Course result = courseService.save(course);
            return ResponseEntity.created(new URI("/api/courses/" + result.getId())).headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getTitle())).body(result);
        }
        catch (ArtemisAuthenticationException ex) {
            // a specified group does not exist, notify the client
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "groupNotFound", ex.getMessage())).body(null);
        }
    }

    /**
     * PUT /courses : Updates an existing updatedCourse.
     *
     * @param updatedCourse the updatedCourse to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated updatedCourse, or with status 400 (Bad Request) if the updatedCourse is not valid, or with status
     *         500 (Internal Server Error) if the updatedCourse couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/courses")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @Transactional
    public ResponseEntity<Course> updateCourse(@RequestBody Course updatedCourse) throws URISyntaxException {
        log.debug("REST request to update Course : {}", updatedCourse);
        if (updatedCourse.getId() == null) {
            return createCourse(updatedCourse);
        }
        Optional<Course> existingCourse = courseRepository.findById(updatedCourse.getId());
        if (!existingCourse.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        // only allow admins or instructors of the existing updatedCourse to change it
        // this is important, otherwise someone could put himself into the instructor group of the updated Course
        if (user.getGroups().contains(existingCourse.get().getInstructorGroupName()) || authCheckService.isAdmin()) {
            try {
                // Check if course shortname matches regex
                Matcher shortNameMatcher = shortNamePattern.matcher(updatedCourse.getShortName());
                if (!shortNameMatcher.matches()) {
                    return ResponseEntity.badRequest().headers(HeaderUtil.createAlert("The shortname is invalid", "shortnameInvalid")).body(null);
                }
                checkIfGroupsExists(updatedCourse);
                Course result = courseService.save(updatedCourse);
                return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, updatedCourse.getTitle())).body(result);
            }
            catch (ArtemisAuthenticationException ex) {
                // a specified group does not exist, notify the client
                return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(ex.getMessage(), "groupNotFound")).body(null);
            }
        }
        else {
            return forbidden();
        }
    }

    private void checkIfGroupsExists(Course course) {
        Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
        if (!activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_PRODUCTION)) {
            return;
        }
        // only execute this method in the production environment because normal developers might not have the right to call this method on the authentication server
        if (course.getInstructorGroupName() != null) {
            if (!artemisAuthenticationProvider.get().checkIfGroupExists(course.getInstructorGroupName())) {
                throw new ArtemisAuthenticationException(
                        "Cannot save! The group " + course.getInstructorGroupName() + " for instructors does not exist. Please double check the instructor group name!");
            }
        }
        if (course.getTeachingAssistantGroupName() != null) {
            if (!artemisAuthenticationProvider.get().checkIfGroupExists(course.getTeachingAssistantGroupName())) {
                throw new ArtemisAuthenticationException("Cannot save! The group " + course.getTeachingAssistantGroupName()
                        + " for teaching assistants does not exist. Please double check the teaching assistants group name!");
            }
        }
        if (course.getStudentGroupName() != null) {
            if (!artemisAuthenticationProvider.get().checkIfGroupExists(course.getStudentGroupName())) {
                throw new ArtemisAuthenticationException(
                        "Cannot save! The group " + course.getStudentGroupName() + " for students does not exist. Please double check the students group name!");
            }
        }
    }

    /**
     * POST /courses/{courseId}/register : Register for an existing course. This method registers the current user for the given course id in case the course has already started
     * and not finished yet. The user is added to the course student group in the Authentication System and the course student group is added to the user's groups in the Artemis
     * database.
     */
    @PostMapping("/courses/{courseId}/register")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<User> registerForCourse(@PathVariable Long courseId) throws URISyntaxException {
        Course course = courseService.findOne(courseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        log.debug("REST request to register {} for Course {}", user.getFirstName(), course.getTitle());
        if (course.getStartDate() != null && course.getStartDate().isAfter(now())) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "courseNotStarted", "The course has not yet started. Cannot register user"))
                    .body(null);
        }
        if (course.getEndDate() != null && course.getEndDate().isBefore(now())) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "courseAlreadyFinished", "The course has already finished. Cannot register user"))
                    .body(null);
        }
        artemisAuthenticationProvider.get().registerUserForCourse(user, course);
        return ResponseEntity.ok(user);
    }

    /**
     * GET /courses : get all courses for administration purposes.
     *
     * @return the list of courses (the user has access to)
     */
    @GetMapping("/courses")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public List<Course> getAllCourses() {
        log.debug("REST request to get all Courses the user has access to");
        User user = userService.getUserWithGroupsAndAuthorities();
        List<Course> courses = courseService.findAll();
        Stream<Course> userCourses = courses.stream().filter(course -> user.getGroups().contains(course.getTeachingAssistantGroupName())
                || user.getGroups().contains(course.getInstructorGroupName()) || authCheckService.isAdmin());
        return userCourses.collect(Collectors.toList());
    }

    /**
     * GET /courses : get all courses that the current user can register to. Decided by the start and end date and if the registrationEnabled flag is set correctly
     *
     * @return the list of courses which are active)
     */
    @GetMapping("/courses/to-register")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public List<Course> getAllCoursesToRegister() {
        log.debug("REST request to get all currently active Courses that are not online courses");
        return courseService.findAllCurrentlyActiveAndNotOnlineAndEnabled();
    }

    /**
     * GET /courses/for-dashboard
     *
     * @param principal the current user principal
     * @return the list of courses (the user has access to) including all exercises with participation and result for the user
     */
    @GetMapping("/courses/for-dashboard")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public List<Course> getAllCoursesForDashboard(Principal principal) {
        long start = System.currentTimeMillis();
        log.debug("REST request to get all Courses the user has access to with exercises, participations and results");
        log.debug("/courses/for-dashboard.start");
        User user = userService.getUserWithGroupsAndAuthorities();

        // get all courses with exercises for this user
        List<Course> courses = courseService.findAllActiveWithExercisesForUser(principal, user);

        log.debug("          /courses/for-dashboard.findAllActiveWithExercisesForUser in " + (System.currentTimeMillis() - start) + "ms");
        // get all participations of this user
        // TODO: can we limit the following call to only retrieve participations and results for active courses?
        // TODO: can we only load the relevant result (the latest rated one which is displayed in the user interface)
        // Idea: we should save the current rated result in Participation and make sure that this is being set correctly when new results are added
        // this would also improve the performance for other REST calls
        List<Participation> participations = participationService.findWithResultsByStudentUsername(principal.getName());
        log.debug("          /courses/for-dashboard.findWithResultsByStudentUsername in " + (System.currentTimeMillis() - start) + "ms");

        long exerciseCount = 0;
        for (Course course : courses) {
            boolean isStudent = !authCheckService.isAtLeastTeachingAssistantInCourse(course, user);
            Set<Lecture> lecturesWithReleasedAttachments = lectureService.filterActiveAttachments(course.getLectures());
            course.setLectures(lecturesWithReleasedAttachments);
            for (Exercise exercise : course.getExercises()) {
                // add participation with result to each exercise
                exercise.filterForCourseDashboard(participations, principal.getName());
                // remove sensitive information from the exercise for students
                if (isStudent) {
                    exercise.filterSensitiveInformation();
                }
                exerciseCount++;
            }
        }
        log.info("/courses/for-dashboard.done in " + (System.currentTimeMillis() - start) + "ms for " + courses.size() + " courses with " + exerciseCount + " exercises for user "
                + principal.getName());
        return courses;
    }

    /**
     * GET /courses/:id/for-tutor-dashboard
     *
     * @param courseId the id of the course to retrieve
     * @return data about a course including all exercises, plus some data for the tutor as tutor status for assessment
     */
    @GetMapping("/courses/{courseId}/for-tutor-dashboard")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Course> getCourseForTutorDashboard(Principal principal, @PathVariable Long courseId) {
        log.debug("REST request /courses/{courseId}/for-tutor-dashboard");
        Course course = courseService.findOne(courseId);
        if (!userHasPermission(course))
            return forbidden();

        User user = userService.getUserWithGroupsAndAuthorities();
        List<Exercise> exercises = exerciseService.findAllForCourse(course, false, principal, user);

        exercises = exercises.stream().filter(exercise -> exercise instanceof TextExercise || exercise instanceof ModelingExercise).collect(Collectors.toList());

        List<TutorParticipation> tutorParticipations = tutorParticipationService.findAllByCourseAndTutor(course, user);

        for (Exercise exercise : exercises) {
            TutorParticipation tutorParticipation = tutorParticipations.stream().filter(participation -> participation.getAssessedExercise().getId().equals(exercise.getId()))
                    .findFirst().orElseGet(() -> {
                        TutorParticipation emptyTutorParticipation = new TutorParticipation();
                        emptyTutorParticipation.setStatus(TutorParticipationStatus.NOT_PARTICIPATED);

                        return emptyTutorParticipation;
                    });

            long numberOfSubmissions = 0;
            if (exercise instanceof TextExercise) {
                numberOfSubmissions = textSubmissionService.countSubmissionsToAssessByExerciseId(exercise.getId());
            }
            else {
                numberOfSubmissions += modelingSubmissionService.countSubmissionsToAssessByExerciseId(exercise.getId());
            }

            long numberOfAssessments = resultService.countNumberOfAssessmentsForExercise(exercise.getId());

            exercise.setNumberOfParticipations((int) numberOfSubmissions);
            exercise.setNumberOfAssessments((int) numberOfAssessments);
            exercise.setTutorParticipations(Collections.singleton(tutorParticipation));
        }

        course.setExercises(new HashSet<>(exercises));

        return ResponseUtil.wrapOrNotFound(Optional.of(course));
    }

    /**
     * GET /courses/:id/stats-for-tutor-dashboard A collection of useful statistics for the tutor course dashboard, including: - number of submissions to the course - number of
     * assessments - number of assessments assessed by the tutor - number of complaints
     *
     * @param courseId the id of the course to retrieve
     * @return data about a course including all exercises, plus some data for the tutor as tutor status for assessment
     */
    @GetMapping("/courses/{courseId}/stats-for-tutor-dashboard")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<JsonNode> getStatsForTutorDashboard(@PathVariable Long courseId) {
        log.debug("REST request /courses/{courseId}/stats-for-tutor-dashboard");

        ObjectNode data = objectMapper.createObjectNode();

        Course course = courseService.findOne(courseId);
        if (!userHasPermission(course))
            return forbidden();
        User user = userService.getUserWithGroupsAndAuthorities();

        long numberOfSubmissions = textSubmissionService.countSubmissionsToAssessByCourseId(courseId);
        numberOfSubmissions += modelingSubmissionService.countSubmissionsToAssessByCourseId(courseId);
        data.set("numberOfSubmissions", objectMapper.valueToTree(numberOfSubmissions));

        long numberOfAssessments = resultService.countNumberOfAssessments(courseId);
        data.set("numberOfAssessments", objectMapper.valueToTree(numberOfAssessments));

        long numberOfTutorAssessments = resultService.countNumberOfAssessmentsForTutor(courseId, user.getId());
        data.set("numberOfTutorAssessments", objectMapper.valueToTree(numberOfTutorAssessments));

        long numberOfComplaints = complaintService.countComplaintsByCourseId(courseId);
        data.set("numberOfComplaints", objectMapper.valueToTree(numberOfComplaints));

        long numberOfTutorComplaints = complaintRepository.countByResult_Participation_Exercise_Course_IdAndResult_Assessor_Id(courseId, user.getId());
        data.set("numberOfTutorComplaints", objectMapper.valueToTree(numberOfTutorComplaints));

        return ResponseEntity.ok(data);
    }

    /**
     * GET /courses/:id : get the "id" course.
     *
     * @param id the id of the course to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the course, or with status 404 (Not Found)
     */
    @GetMapping("/courses/{id}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Course> getCourse(@PathVariable Long id) {
        log.debug("REST request to get Course : {}", id);
        Course course = courseService.findOne(id);
        if (!userHasPermission(course))
            return forbidden();
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(course));
    }

    /**
     * GET /courses/:id : get the "id" course.
     *
     * @param id the id of the course to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the course, or with status 404 (Not Found)
     */
    @GetMapping("/courses/{id}/with-exercises")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Course> getCourseWithExercises(@PathVariable Long id) {
        log.debug("REST request to get Course : {}", id);
        Course course = courseService.findOneWithExercises(id);
        if (!userHasPermission(course))
            return forbidden();
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(course));
    }

    /**
     * GET /courses/:id/with-exercises-and-relevant-participations Get the "id" course, with text and modelling exercises and their participations It can be used only by
     * instructors for the instructor dashboard
     *
     * @param courseId the id of the course to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the course, or with status 404 (Not Found)
     */
    @GetMapping("/courses/{courseId}/with-exercises-and-relevant-participations")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Course> getCourseWithExercisesAndRelevantParticipations(@PathVariable Long courseId) throws AccessForbiddenException {
        log.debug("REST request to get Course with exercises and relevant participations : {}", courseId);
        long start = System.currentTimeMillis();
        Course course = courseService.findOneWithExercises(courseId);

        if (!userHasPermission(course)) {
            throw new AccessForbiddenException("You are not allowed to access this resource");
        }

        Set<Exercise> interestingExercises = course.getExercises().stream().filter(exercise -> exercise instanceof TextExercise || exercise instanceof ModelingExercise)
                .collect(Collectors.toSet());

        course.setExercises(interestingExercises);

        for (Exercise exercise : interestingExercises) {
            long numberOfParticipations = 0;
            if (exercise instanceof TextExercise) {
                numberOfParticipations = textSubmissionService.countSubmissionsToAssessByExerciseId(exercise.getId());
            }
            else {
                numberOfParticipations += modelingSubmissionService.countSubmissionsToAssessByExerciseId(exercise.getId());
            }

            long numberOfAssessments = resultService.countNumberOfAssessmentsForExercise(exercise.getId());
            long numberOfComplaints = complaintService.countComplaintsByExerciseId(exercise.getId());
            exercise.setNumberOfParticipations((int) numberOfParticipations);
            exercise.setNumberOfAssessments((int) numberOfAssessments);
            exercise.setNumberOfComplaints((int) numberOfComplaints);
        }
        long end = System.currentTimeMillis();
        log.info("Finished /courses/" + courseId + "/with-exercises-and-relevant-participations call in " + (end - start) + "ms");
        return ResponseUtil.wrapOrNotFound(Optional.of(course));
    }

    /**
     * GET /courses/:id/stats-for-instructor-dashboard
     * <p>
     * A collection of useful statistics for the instructor course dashboard, including: - number of students - number of instructors - number of submissions - number of
     * assessments - number of complaints - number of open complaints - tutor leaderboard data
     *
     * @param courseId the id of the course to retrieve
     * @return data about a course including all exercises, plus some data for the tutor as tutor status for assessment
     */
    @GetMapping("/courses/{courseId}/stats-for-instructor-dashboard")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<StatsForInstructorDashboardDTO> getStatsForInstructorDashboard(@PathVariable Long courseId) throws AccessForbiddenException {
        log.debug("REST request /courses/{courseId}/stats-for-instructor-dashboard");
        long start = System.currentTimeMillis();
        Course course = courseService.findOne(courseId);
        if (!userHasPermission(course)) {
            throw new AccessForbiddenException("You are not allowed to access this resource");
        }

        StatsForInstructorDashboardDTO stats = new StatsForInstructorDashboardDTO();

        long numberOfComplaints = complaintRepository.countByResult_Participation_Exercise_Course_Id(courseId);
        long numberOfComplaintResponses = complaintResponseRepository.countByComplaint_Result_Participation_Exercise_Course_Id(courseId);

        stats.numberOfStudents = courseService.countNumberOfStudentsForCourse(course);
        stats.numberOfTutors = courseService.countNumberOfTutorsForCourse(course);
        stats.numberOfComplaints = numberOfComplaints;
        stats.numberOfOpenComplaints = numberOfComplaints - numberOfComplaintResponses;

        long numberOfSubmissions = textSubmissionService.countSubmissionsToAssessByCourseId(courseId);
        numberOfSubmissions += modelingSubmissionService.countSubmissionsToAssessByCourseId(courseId);

        stats.numberOfSubmissions = numberOfSubmissions;
        stats.numberOfAssessments = resultService.countNumberOfAssessments(courseId);

        log.info("Finished simple stats in " + (System.currentTimeMillis() - start) + "ms");
        stats.tutorLeaderboard = textAssessmentService.calculateTutorLeaderboardForCourse(courseId);
        log.info("Finished /courses/" + courseId + "/stats-for-instructor-dashboard call in " + (System.currentTimeMillis() - start) + "ms");
        return ResponseEntity.ok(stats);
    }

    private boolean userHasPermission(Course course) {
        User user = userService.getUserWithGroupsAndAuthorities();
        return authCheckService.isTeachingAssistantInCourse(course, user) || authCheckService.isInstructorInCourse(course, user) || authCheckService.isAdmin();
    }

    /**
     * DELETE /courses/:id : delete the "id" course.
     *
     * @param id the id of the course to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/courses/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Transactional
    public ResponseEntity<Void> deleteCourse(@PathVariable Long id) {
        log.debug("REST request to delete Course : {}", id);
        Course course = courseService.findOne(id);
        if (course == null) {
            return ResponseEntity.notFound().build();
        }
        for (Exercise exercise : course.getExercises()) {
            exerciseService.delete(exercise, false, false);
        }

        for (Lecture lecture : course.getLectures()) {
            lectureService.delete(lecture);
        }

        List<GroupNotification> notifications = notificationService.findAllNotificationsForCourse(course);
        for (GroupNotification notification : notifications) {
            notificationService.deleteNotification(notification);
        }
        String title = course.getTitle();
        courseService.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, title)).build();
    }

    /**
     * GET /courses/:courseId/results : Returns all results of the exercises of a course for the currently logged in user
     *
     * @param courseId the id of the course to get the results from
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @GetMapping(value = "/courses/{courseId}/results")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<Course> getResultsForCurrentStudent(@PathVariable Long courseId) {
        long start = System.currentTimeMillis();
        log.debug("REST request to get Results for Course and current Studen : {}", courseId);

        User student = userService.getUser();
        Course course = courseService.findOne(courseId);
        boolean isStudent = !authCheckService.isAtLeastTeachingAssistantInCourse(course, student);

        List<Exercise> exercises = exerciseService.findAllExercisesByCourseId(course, student);

        for (Exercise exercise : exercises) {
            List<Participation> participations = participationService.findByExerciseIdAndStudentIdWithEagerResults(exercise.getId(), student.getId());

            exercise.setParticipations(new HashSet<>());

            // Removing not needed properties and sensitive information for students
            exercise.setCourse(null);
            if (isStudent) {
                exercise.filterSensitiveInformation();
            }

            for (Participation participation : participations) {
                // Removing not needed properties
                participation.setStudent(null);

                participation.setResults(participation.getResults());
                exercise.addParticipation(participation);
            }
            course.addExercises(exercise);
        }

        log.debug("getResultsForCurrentStudent took " + (System.currentTimeMillis() - start) + "ms");

        return ResponseEntity.ok().body(course);
    }

    /**
     * GET /courses/:courseId/categories : Returns all categories used in a course
     *
     * @param courseId the id of the course to get the categories from
     * @return the ResponseEntity with status 200 (OK) and the list of categories or with status 404 (Not Found)
     */
    @GetMapping(value = "/courses/{courseId}/categories")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<String>> getCategoriesInCourse(@PathVariable Long courseId) {
        long start = System.currentTimeMillis();
        log.debug("REST request to get categories of Course : {}", courseId);

        User user = userService.getUser();
        Course course = courseService.findOne(courseId);

        List<Exercise> exercises = exerciseService.findAllExercisesByCourseId(course, user);
        List<String> categories = new ArrayList<>();
        for (Exercise exercise : exercises) {
            categories.addAll(exercise.getCategories());
        }

        log.debug("getCategoriesInCourse took " + (System.currentTimeMillis() - start) + "ms");

        return ResponseEntity.ok().body(categories);
    }
}
