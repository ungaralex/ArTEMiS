package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.assertj.core.api.Fail;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.util.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
public class ModelingSubmissionIntegrationTest {

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    UserRepository userRepo;

    @Autowired
    RequestUtilService request;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    ParticipationService participationService;

    @Autowired
    ResultRepository resultRepo;

    @Autowired
    ModelingSubmissionRepository modelingSubmissionRepo;

    private ModelingExercise classExercise;

    private ModelingExercise activityExercise;

    private ModelingExercise objectExercise;

    private ModelingExercise useCaseExercise;

    ModelingSubmission submittedSubmission;

    ModelingSubmission unsubmittedSubmission;

    String emptyModel;

    String validModel;

    @Before
    public void initTestCase() throws Exception {
        database.resetDatabase();
        database.addUsers(2, 1);
        database.addCourseWithDifferentModelingExercises();
        classExercise = (ModelingExercise) exerciseRepo.findAll().get(0);
        activityExercise = (ModelingExercise) exerciseRepo.findAll().get(1);
        objectExercise = (ModelingExercise) exerciseRepo.findAll().get(2);
        useCaseExercise = (ModelingExercise) exerciseRepo.findAll().get(3);
        emptyModel = database.loadFileFromResources("test-data/model-submission/empty-model.json");
        validModel = database.loadFileFromResources("test-data/model-submission/model.54727.json");
        submittedSubmission = generateSubmittedSubmission();
        unsubmittedSubmission = generateUnsubmittedSubmission();
    }

    @Ignore
    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void modelingSubmissionOfStudent_classDiagram() throws Exception {
        database.addParticipationForExercise(classExercise, "student1");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(emptyModel, false);
        ModelingSubmission returnedSubmission = performInitialModelSubmission(classExercise.getId(), submission);
        database.checkSubmissionCorrectlyStored(returnedSubmission.getId(), emptyModel);
        checkDetailsHidden(returnedSubmission);

        submission = ModelFactory.generateModelingSubmission(validModel, true);
        returnedSubmission = performUpdateOnModelSubmission(classExercise.getId(), submission);
        database.checkSubmissionCorrectlyStored(returnedSubmission.getId(), validModel);
        checkDetailsHidden(returnedSubmission);
    }

    @Ignore
    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void modelingSubmissionOfStudent_activityDiagram() throws Exception {
        database.addParticipationForExercise(activityExercise, "student1");
        String emptyActivityModel = database.loadFileFromResources("test-data/model-submission/empty-activity-model.json");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(emptyActivityModel, false);
        ModelingSubmission returnedSubmission = performInitialModelSubmission(activityExercise.getId(), submission);
        database.checkSubmissionCorrectlyStored(returnedSubmission.getId(), emptyActivityModel);
        checkDetailsHidden(returnedSubmission);
        String validActivityModel = database.loadFileFromResources("test-data/model-submission/activity-model.json");
        submission = ModelFactory.generateModelingSubmission(validActivityModel, true);
        returnedSubmission = performUpdateOnModelSubmission(activityExercise.getId(), submission);
        database.checkSubmissionCorrectlyStored(returnedSubmission.getId(), validActivityModel);
        checkDetailsHidden(returnedSubmission);
    }

    @Ignore
    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void modelingSubmissionOfStudent_objectDiagram() throws Exception {
        database.addParticipationForExercise(objectExercise, "student1");
        String emptyObjectModel = database.loadFileFromResources("test-data/model-submission/empty-object-model.json");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(emptyObjectModel, false);
        ModelingSubmission returnedSubmission = performInitialModelSubmission(objectExercise.getId(), submission);
        database.checkSubmissionCorrectlyStored(returnedSubmission.getId(), emptyObjectModel);
        checkDetailsHidden(returnedSubmission);
        String validObjectModel = database.loadFileFromResources("test-data/model-submission/object-model.json");
        submission = ModelFactory.generateModelingSubmission(validObjectModel, true);
        returnedSubmission = performUpdateOnModelSubmission(objectExercise.getId(), submission);
        database.checkSubmissionCorrectlyStored(returnedSubmission.getId(), validObjectModel);
        checkDetailsHidden(returnedSubmission);
    }

    @Ignore
    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void modelingSubmissionOfStudent_useCaseDiagram() throws Exception {
        database.addParticipationForExercise(useCaseExercise, "student1");
        String emptyUseCaseModel = database.loadFileFromResources("test-data/model-submission/empty-use-case-model.json");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(emptyUseCaseModel, false);
        ModelingSubmission returnedSubmission = performInitialModelSubmission(useCaseExercise.getId(), submission);
        database.checkSubmissionCorrectlyStored(returnedSubmission.getId(), emptyUseCaseModel);
        checkDetailsHidden(returnedSubmission);
        String validUseCaseModel = database.loadFileFromResources("test-data/model-submission/use-case-model.json");
        submission = ModelFactory.generateModelingSubmission(validUseCaseModel, true);
        returnedSubmission = performUpdateOnModelSubmission(useCaseExercise.getId(), submission);
        database.checkSubmissionCorrectlyStored(returnedSubmission.getId(), validUseCaseModel);
        checkDetailsHidden(returnedSubmission);
    }

    @Ignore
    @Test
    @WithMockUser(value = "student2", roles = "USER")
    public void updateModelSubmissionAfterSubmit() throws Exception {
        database.addParticipationForExercise(classExercise, "student2");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(emptyModel, false);
        ModelingSubmission returnedSubmission = performInitialModelSubmission(classExercise.getId(), submission);
        database.checkSubmissionCorrectlyStored(returnedSubmission.getId(), emptyModel);
        submission = ModelFactory.generateModelingSubmission(validModel, false);
        try {
            returnedSubmission = performUpdateOnModelSubmission(classExercise.getId(), submission);
            database.checkSubmissionCorrectlyStored(returnedSubmission.getId(), validModel);
            checkDetailsHidden(returnedSubmission);
            Fail.fail("update on submitted ModelingSubmission worked");
        }
        catch (Exception e) {
        }
    }

    @Ignore
    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void injectResultOnSubmissionUpdate() throws Exception {
        User user = userRepo.findOneByLogin("student1").get();
        database.addParticipationForExercise(classExercise, "student1");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, false);
        Result result = new Result();
        result.setScore(100L);
        result.setRated(true);
        result.setAssessor(user);
        submission.setResult(result);
        ModelingSubmission storedSubmission = request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submission,
                ModelingSubmission.class);
        storedSubmission = modelingSubmissionRepo.findById(storedSubmission.getId()).get();
        assertThat(storedSubmission.getResult()).as("submission still unrated").isNull();
    }

    @Ignore
    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getAllSubmissionsOfExercise() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmission(classExercise, submittedSubmission, "student1");
        ModelingSubmission submission2 = database.addModelingSubmission(classExercise, unsubmittedSubmission, "student2");
        List<ModelingSubmission> submissions = request.getList("/api/exercises/" + classExercise.getId() + "/modeling-submissions", HttpStatus.OK, ModelingSubmission.class);
        assertThat(submissions).as("contains both submissions").containsExactlyInAnyOrder(new ModelingSubmission[] { submission1, submission2 });
    }

    @Ignore
    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void getAllSubmissionsOfExerciseAsStudent() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmission(classExercise, submittedSubmission, "student1");
        ModelingSubmission submission2 = database.addModelingSubmission(classExercise, unsubmittedSubmission, "student2");
        request.getList("/api/exercises/" + classExercise.getId() + "/modeling-submissions", HttpStatus.FORBIDDEN, ModelingSubmission.class);
        request.getList("/api/exercises/" + classExercise.getId() + "/modeling-submissions?submittedOnly=true", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Ignore
    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getAllSubmittedSubmissionsOfExercise() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmission(classExercise, unsubmittedSubmission, "student1");
        ModelingSubmission submission2 = database.addModelingSubmission(classExercise, submittedSubmission, "student1");
        ModelingSubmission submission3 = database.addModelingSubmission(classExercise, generateSubmittedSubmission(), "student2");
        List<ModelingSubmission> submissions = request.getList("/api/exercises/" + classExercise.getId() + "/modeling-submissions?submittedOnly=true", HttpStatus.OK,
                ModelingSubmission.class);
        assertThat(submissions).as("contains only submitted submission").containsExactlyInAnyOrder(new ModelingSubmission[] { submission1, submission3 });
    }

    @Ignore
    @Test
    @WithMockUser(value = "tutor1")
    public void getModelSubmission() throws Exception {
        User user = userRepo.findOneByLogin("tutor1").get();
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        submission = database.addModelingSubmission(classExercise, submission, "student1");
        ModelingSubmission storedSubmission = request.get("/api/modeling-submissions/" + submission.getId(), HttpStatus.OK, ModelingSubmission.class);
        assertThat(storedSubmission.getResult()).as("result has been set").isNotNull();
        assertThat(storedSubmission.getResult().getAssessor()).as("assessor is tutor1").isEqualTo(user);
        checkDetailsHidden(storedSubmission);
    }

    @Ignore
    @Test
    @WithMockUser(value = "student1")
    public void getModelSubmissionAsStudent() throws Exception {
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        submission = database.addModelingSubmission(classExercise, submission, "student1");
        request.get("/api/modeling-submissions/" + submission.getId(), HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    private void checkDetailsHidden(ModelingSubmission submission) {
        assertThat(submission.getParticipation().getSubmissions()).isNullOrEmpty();
        assertThat(submission.getParticipation().getResults()).isNullOrEmpty();
        assertThat(((ModelingExercise) submission.getParticipation().getExercise()).getSampleSolutionModel()).isNullOrEmpty();
        assertThat(((ModelingExercise) submission.getParticipation().getExercise()).getSampleSolutionExplanation()).isNullOrEmpty();
    }

    private ModelingSubmission performInitialModelSubmission(Long exerciseId, ModelingSubmission submission) throws Exception {
        return request.postWithResponseBody("/api/exercises/" + exerciseId + "/modeling-submissions", submission, ModelingSubmission.class, HttpStatus.OK);
    }

    private ModelingSubmission performUpdateOnModelSubmission(Long exerciseId, ModelingSubmission submission) throws Exception {
        return request.putWithResponseBody("/api/exercises/" + exerciseId + "/modeling-submissions", submission, ModelingSubmission.class, HttpStatus.OK);
    }

    private ModelingSubmission generateSubmittedSubmission() {
        return ModelFactory.generateModelingSubmission(emptyModel, true);
    }

    private ModelingSubmission generateUnsubmittedSubmission() {
        return ModelFactory.generateModelingSubmission(emptyModel, true);
    }
}
