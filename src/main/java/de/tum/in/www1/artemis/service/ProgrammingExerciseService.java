package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.PROGRAMMING_SUBMISSION_RESOURCE_API_PATH;
import static de.tum.in.www1.artemis.config.Constants.TEST_CASE_CHANGED_API_PATH;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.*;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationUpdateService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.service.util.structureoraclegenerator.OracleGeneratorClient;

@Service
@Transactional
public class ProgrammingExerciseService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseService.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final FileService fileService;

    private final GitService gitService;

    private final Optional<VersionControlService> versionControlService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final Optional<ContinuousIntegrationUpdateService> continuousIntegrationUpdateService;

    private final SubmissionRepository submissionRepository;

    private final ParticipationRepository participationRepository;

    private final ResourceLoader resourceLoader;

    @Value("${server.url}")
    private String ARTEMIS_BASE_URL;

    public ProgrammingExerciseService(ProgrammingExerciseRepository programmingExerciseRepository, FileService fileService, GitService gitService,
            Optional<VersionControlService> versionControlService, Optional<ContinuousIntegrationService> continuousIntegrationService,
            Optional<ContinuousIntegrationUpdateService> continuousIntegrationUpdateService, ResourceLoader resourceLoader, SubmissionRepository submissionRepository,
            ParticipationRepository participationRepository) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.fileService = fileService;
        this.gitService = gitService;
        this.versionControlService = versionControlService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.continuousIntegrationUpdateService = continuousIntegrationUpdateService;
        this.resourceLoader = resourceLoader;
        this.participationRepository = participationRepository;
        this.submissionRepository = submissionRepository;
    }

    /**
     * Notifies all particpations of the given programmingExercise about changes of the test cases.
     *
     * @param programmingExercise The programmingExercise where the test cases got changed
     */
    public void notifyChangedTestCases(ProgrammingExercise programmingExercise, Object requestBody) {
        for (Participation participation : programmingExercise.getParticipations()) {

            ProgrammingSubmission submission = new ProgrammingSubmission();
            submission.setType(SubmissionType.TEST);
            submission.setSubmissionDate(ZonedDateTime.now());
            submission.setSubmitted(true);
            submission.setParticipation(participation);
            try {
                String lastCommitHash = versionControlService.get().getLastCommitHash(requestBody);
                log.info("create new programmingSubmission with commitHash: " + lastCommitHash);
                submission.setCommitHash(lastCommitHash);
            }
            catch (Exception ex) {
                log.error("Commit hash could not be parsed for submission from participation " + participation, ex);
            }

            submissionRepository.save(submission);
            participationRepository.save(participation);

            continuousIntegrationUpdateService.get().triggerUpdate(participation.getBuildPlanId(), false);
        }
    }

    public void addStudentIdToProjectName(Repository repo, ProgrammingExercise programmingExercise, Participation participation) {
        String studentId = participation.getStudent().getLogin();

        // Get all files in repository expect .git files
        List<String> allRepoFiles = listAllFilesInPath(repo.getLocalPath());

        // is Java programming language
        if (programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA) {
            // Filter all Eclipse .project files
            List<String> eclipseProjectFiles = allRepoFiles.stream().filter(s -> s.endsWith(".project")).collect(Collectors.toList());

            for (String eclipseProjectFilePath : eclipseProjectFiles) {
                File eclipseProjectFile = new File(eclipseProjectFilePath);
                // Check if file exists and full file name is .project and not just the file ending.
                if (!eclipseProjectFile.exists() || !eclipseProjectFile.getName().equals(".project")) {
                    continue;
                }

                try {
                    // 1- Build the doc from the XML file
                    Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(eclipseProjectFile.getPath()));
                    doc.setXmlStandalone(true);

                    // 2- Find the node with xpath
                    XPath xPath = XPathFactory.newInstance().newXPath();
                    Node nameNode = (Node) xPath.compile("/projectDescription/name").evaluate(doc, XPathConstants.NODE);

                    // 3- Append Student Id to Project Name
                    if (nameNode != null) {
                        nameNode.setTextContent(nameNode.getTextContent() + " " + studentId);
                    }

                    // 4- Save the result to a new XML doc
                    Transformer xformer = TransformerFactory.newInstance().newTransformer();
                    xformer.transform(new DOMSource(doc), new StreamResult(new File(eclipseProjectFile.getPath())));

                }
                catch (SAXException | IOException | ParserConfigurationException | TransformerException | XPathException ex) {
                    log.error("Cannot rename .project file in " + repo.getLocalPath() + " due to the following exception: " + ex);
                }
            }

            // Filter all pom.xml files
            List<String> pomFiles = allRepoFiles.stream().filter(s -> s.endsWith("pom.xml")).collect(Collectors.toList());
            for (String pomFilePath : pomFiles) {
                File pomFile = new File(pomFilePath);
                // check if file exists and full file name is pom.xml and not just the file ending.
                if (!pomFile.exists() || !pomFile.getName().equals("pom.xml")) {
                    continue;
                }

                try {
                    // 1- Build the doc from the XML file
                    Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(pomFile.getPath()));
                    doc.setXmlStandalone(true);

                    // 2- Find the relevant nodes with xpath
                    XPath xPath = XPathFactory.newInstance().newXPath();
                    Node nameNode = (Node) xPath.compile("/project/name").evaluate(doc, XPathConstants.NODE);
                    Node artifactIdNode = (Node) xPath.compile("/project/artifactId").evaluate(doc, XPathConstants.NODE);

                    // 3- Append Student Id to Project Names
                    if (nameNode != null) {
                        nameNode.setTextContent(nameNode.getTextContent() + " " + studentId);
                    }
                    if (artifactIdNode != null) {
                        String artifactId = (artifactIdNode.getTextContent() + "-" + studentId).replaceAll(" ", "-").toLowerCase();
                        artifactIdNode.setTextContent(artifactId);
                    }

                    // 4- Save the result to a new XML doc
                    Transformer xformer = TransformerFactory.newInstance().newTransformer();
                    xformer.transform(new DOMSource(doc), new StreamResult(new File(pomFile.getPath())));

                }
                catch (SAXException | IOException | ParserConfigurationException | TransformerException | XPathException ex) {
                    log.error("Cannot rename pom.xml file in " + repo.getLocalPath() + " due to the following exception: " + ex);
                }
            }
        }

        try {
            gitService.stageAllChanges(repo);
            gitService.commit(repo, "Add Student Id to Project Name");
        }
        catch (GitAPIException ex) {
            log.error("Cannot stage or commit to the repo " + repo.getLocalPath() + " due to the following exception: " + ex);
        }
    }

    /**
     * Get all files in path expect .git files
     *
     * @param path
     */
    private List<String> listAllFilesInPath(Path path) {
        List<String> allRepoFiles = null;
        try (Stream<Path> walk = Files.walk(path)) {
            allRepoFiles = walk.filter(Files::isRegularFile).map(x -> x.toString()).filter(s -> !s.contains(".git")).collect(Collectors.toList());
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return allRepoFiles;
    }

    /**
     * Setups all needed repositories etc. for the given programmingExercise.
     *
     * @param programmingExercise The programmingExercise that should be setup
     */
    public ProgrammingExercise setupProgrammingExercise(ProgrammingExercise programmingExercise) throws Exception {
        String projectKey = programmingExercise.getProjectKey();
        String exerciseRepoName = projectKey.toLowerCase() + "-exercise";
        String testRepoName = projectKey.toLowerCase() + "-tests";
        String solutionRepoName = projectKey.toLowerCase() + "-solution";

        // Create VCS repositories
        versionControlService.get().createProjectForExercise(programmingExercise); // Create project
        versionControlService.get().createRepository(projectKey, exerciseRepoName, null); // Create template repository
        versionControlService.get().createRepository(projectKey, testRepoName, null); // Create tests repository
        versionControlService.get().createRepository(projectKey, solutionRepoName, null); // Create solution repository

        Participation templateParticipation = programmingExercise.getTemplateParticipation();
        if (templateParticipation == null) {
            templateParticipation = new Participation();
            programmingExercise.setTemplateParticipation(templateParticipation);
        }
        Participation solutionParticipation = programmingExercise.getSolutionParticipation();
        if (solutionParticipation == null) {
            solutionParticipation = new Participation();
            programmingExercise.setSolutionParticipation(solutionParticipation);
        }

        initParticipations(programmingExercise);

        templateParticipation.setBuildPlanId(projectKey + "-BASE"); // Set build plan id to newly created BaseBuild plan
        templateParticipation.setRepositoryUrl(versionControlService.get().getCloneURL(projectKey, exerciseRepoName).toString());
        solutionParticipation.setBuildPlanId(projectKey + "-SOLUTION");
        solutionParticipation.setRepositoryUrl(versionControlService.get().getCloneURL(projectKey, solutionRepoName).toString());
        programmingExercise.setTestRepositoryUrl(versionControlService.get().getCloneURL(projectKey, testRepoName).toString());

        // Save participations to get the ids required for the webhooks
        templateParticipation.setExercise(programmingExercise);
        solutionParticipation.setExercise(programmingExercise);
        templateParticipation = participationRepository.save(templateParticipation);
        solutionParticipation = participationRepository.save(solutionParticipation);

        URL exerciseRepoUrl = versionControlService.get().getCloneURL(projectKey, exerciseRepoName);
        URL testsRepoUrl = versionControlService.get().getCloneURL(projectKey, testRepoName);
        URL solutionRepoUrl = versionControlService.get().getCloneURL(projectKey, solutionRepoName);

        String programmingLanguage = programmingExercise.getProgrammingLanguage().toString().toLowerCase();

        String templatePath = "classpath:templates/" + programmingLanguage;
        String exercisePath = templatePath + "/exercise/**/*.*";
        String solutionPath = templatePath + "/solution/**/*.*";
        String testPath = templatePath + "/test/**/*.*";

        Resource[] exerciseResources = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources(exercisePath);
        Resource[] testResources = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources(testPath);
        Resource[] solutionResources = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources(solutionPath);

        Repository exerciseRepo = gitService.getOrCheckoutRepository(exerciseRepoUrl);
        Repository testRepo = gitService.getOrCheckoutRepository(testsRepoUrl);
        Repository solutionRepo = gitService.getOrCheckoutRepository(solutionRepoUrl);

        try {
            String exercisePrefix = programmingLanguage + File.separator + "exercise";
            String testPrefix = programmingLanguage + File.separator + "test";
            String solutionPrefix = programmingLanguage + File.separator + "solution";
            setupTemplateAndPush(exerciseRepo, exerciseResources, exercisePrefix, "Exercise", programmingExercise);
            setupTemplateAndPush(testRepo, testResources, testPrefix, "Test", programmingExercise);
            setupTemplateAndPush(solutionRepo, solutionResources, solutionPrefix, "Solution", programmingExercise);

        }
        catch (Exception ex) {
            // if any exception occurs, try to at least push an empty commit, so that the
            // repositories can
            // be used by the build plans
            log.warn("An exception occurred while setting up the repositories", ex);
            gitService.commitAndPush(exerciseRepo, "Empty Setup by Artemis");
            gitService.commitAndPush(testRepo, "Empty Setup by Artemis");
            gitService.commitAndPush(solutionRepo, "Empty Setup by Artemis");
        }

        // The creation of the webhooks must occur after the initial push, because the
        // participation is
        // not yet saved in the database, so we cannot save the submission accordingly
        // (see
        // ProgrammingSubmissionService.notifyPush)
        versionControlService.get().addWebHook(templateParticipation.getRepositoryUrlAsUrl(),
                ARTEMIS_BASE_URL + PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + templateParticipation.getId(), "ArTEMiS WebHook");
        versionControlService.get().addWebHook(solutionParticipation.getRepositoryUrlAsUrl(),
                ARTEMIS_BASE_URL + PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + solutionParticipation.getId(), "ArTEMiS WebHook");

        // We have to wait to have pushed one commit to each repository as we can only
        // create the
        // buildPlans then
        // (https://confluence.atlassian.com/bamkb/cannot-create-linked-repository-or-plan-repository-942840872.html)
        continuousIntegrationService.get().createBuildPlanForExercise(programmingExercise, "BASE", exerciseRepoName, testRepoName); // plan for the exercise (students)
        continuousIntegrationService.get().createBuildPlanForExercise(programmingExercise, "SOLUTION", solutionRepoName, testRepoName); // plan for the solution (instructors) with
                                                                                                                                        // solution repository

        // save to get the id required for the webhook
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        versionControlService.get().addWebHook(testsRepoUrl, ARTEMIS_BASE_URL + TEST_CASE_CHANGED_API_PATH + programmingExercise.getId(), "ArTEMiS Tests WebHook");
        return programmingExercise;
    }

    // Copy template and push, if no file is in the directory
    private void setupTemplateAndPush(Repository repository, Resource[] resources, String prefix, String templateName, ProgrammingExercise programmingExercise) throws Exception {
        if (gitService.listFiles(repository).size() == 0) { // Only copy template if repo is empty
            fileService.copyResources(resources, prefix, repository.getLocalPath().toAbsolutePath().toString());
            if (programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA) {
                fileService.replaceVariablesInDirectoryName(repository.getLocalPath().toAbsolutePath().toString(), "${packageNameFolder}",
                        programmingExercise.getPackageFolderName());
            }
            // there is no need in python to replace package names

            List<String> fileTargets = new ArrayList<>();
            List<String> fileReplacements = new ArrayList<>();
            // This is based on the correct order and assumes that boths lists have the same
            // length, it
            // replaces fileTargets.get(i) with fileReplacements.get(i)

            if (programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA) {
                fileTargets.add("${packageName}");
                fileReplacements.add(programmingExercise.getPackageName());
            }
            // there is no need in python to replace package names

            fileTargets.add("${exerciseNameCompact}");
            fileReplacements.add(programmingExercise.getShortName().toLowerCase()); // Used e.g. in artifactId

            fileTargets.add("${exerciseName}");
            fileReplacements.add(programmingExercise.getTitle());

            fileService.replaceVariablesInFileRecursive(repository.getLocalPath().toAbsolutePath().toString(), fileTargets, fileReplacements);

            gitService.stageAllChanges(repository);
            gitService.commitAndPush(repository, templateName + "-Template pushed by Artemis");
            repository.setFiles(null); // Clear cache to avoid multiple commits when ArTEMiS server is not restarted between attempts
        }
    }

    /**
     * Find the ProgrammingExercise where the given Participation is the template Participation
     *
     * @param participation The template participation
     * @return The ProgrammingExercise where the given Participation is the template Participation
     */
    public ProgrammingExercise getExerciseForTemplateParticipation(Participation participation) {
        return programmingExerciseRepository.findOneByTemplateParticipationId(participation.getId());
    }

    /**
     * Find the ProgrammingExercise where the given Participation is the solution Participation
     *
     * @param participation The solution participation
     * @return The ProgrammingExercise where the given Participation is the solution Participation
     */
    public ProgrammingExercise getExerciseForSolutionParticipation(Participation participation) {
        return programmingExerciseRepository.findOneBySolutionParticipationId(participation.getId());
    }

    /**
     * This methods sets the values (initialization date and initialization state) of the template and solution participation
     *
     * @param programmingExercise The programming exercise
     */
    public void initParticipations(ProgrammingExercise programmingExercise) {

        Participation solutionParticipation = programmingExercise.getSolutionParticipation();
        Participation templateParticipation = programmingExercise.getTemplateParticipation();

        solutionParticipation.setInitializationState(InitializationState.INITIALIZED);
        templateParticipation.setInitializationState(InitializationState.INITIALIZED);
        solutionParticipation.setInitializationDate(ZonedDateTime.now());
        templateParticipation.setInitializationDate(ZonedDateTime.now());
    }

    /**
     * This method saves the participations of the programming xercise
     *
     * @param programmingExercise The programming exercise
     */
    public void saveParticipations(ProgrammingExercise programmingExercise) {
        Participation solutionParticipation = programmingExercise.getSolutionParticipation();
        Participation templateParticipation = programmingExercise.getTemplateParticipation();

        participationRepository.save(solutionParticipation);
        participationRepository.save(templateParticipation);
    }

    /**
     * This method calls the StructureOracleGenerator, generates the string out of the JSON representation of the structure oracle of the programming exercise and returns true if
     * the file was updated or generated, false otherwise. This can happen if the contents of the file have not changed.
     *
     * @param solutionRepoURL The URL of the solution repository.
     * @param templateRepoURL The URL of the exercise repository.
     * @param testRepoURL     The URL of the tests repository.
     * @param testsPath       The path to the tests folder, e.g. the path inside the repository where the structure oracle file will be saved in.
     * @return True, if the structure oracle was successfully generated or updated, false if no changes to the file were made.
     * @throws IOException
     * @throws InterruptedException
     */
    public boolean generateStructureOracleFile(URL solutionRepoURL, URL templateRepoURL, URL testRepoURL, String testsPath) throws IOException, InterruptedException {
        Repository solutionRepository = gitService.getOrCheckoutRepository(solutionRepoURL);
        Repository templateRepository = gitService.getOrCheckoutRepository(templateRepoURL);
        Repository testRepository = gitService.getOrCheckoutRepository(testRepoURL);

        // Hard reset the repository, otherwise it can fail if e.g. a force push was made to one of the repositories.
        gitService.pull(solutionRepository, true);
        gitService.pull(templateRepository, true);
        gitService.pull(testRepository, true);

        Path solutionRepositoryPath = solutionRepository.getLocalPath().toRealPath();
        Path exerciseRepositoryPath = templateRepository.getLocalPath().toRealPath();
        Path structureOraclePath = Paths.get(testRepository.getLocalPath().toRealPath().toString(), testsPath, "test.json");

        String structureOracleJSON = OracleGeneratorClient.generateStructureOracleJSON(solutionRepositoryPath, exerciseRepositoryPath);

        // If the oracle file does not already exist, then save the generated string to
        // the file.
        // If it does, check if the contents of the existing file are the same as the
        // generated one.
        // If they are, do not push anything and inform the user about it.
        // If not, then update the oracle file by rewriting it and push the changes.
        if (!Files.exists(structureOraclePath)) {
            try {
                Files.write(structureOraclePath, structureOracleJSON.getBytes());
                gitService.stageAllChanges(testRepository);
                gitService.commitAndPush(testRepository, "Generate the structure oracle file.");
                return true;
            }
            catch (GitAPIException e) {
                log.error("An exception occurred while pushing the structure oracle file to the test repository.", e);
                return false;
            }
        }
        else {
            Byte[] existingContents = ArrayUtils.toObject(Files.readAllBytes(structureOraclePath));
            Byte[] newContents = ArrayUtils.toObject(structureOracleJSON.getBytes());

            if (Arrays.deepEquals(existingContents, newContents)) {
                log.info("No changes to the oracle detected.");
                return false;
            }
            else {
                try {
                    Files.write(structureOraclePath, structureOracleJSON.getBytes());
                    gitService.stageAllChanges(testRepository);
                    gitService.commitAndPush(testRepository, "Update the structure oracle file.");
                    return true;
                }
                catch (GitAPIException e) {
                    log.error("An exception occurred while pushing the structure oracle file to the test repository.", e);
                    return false;
                }
            }
        }
    }
}
