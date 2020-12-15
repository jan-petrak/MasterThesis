package jan.tuning;

import vahy.api.benchmark.EpisodeStatistics;
import vahy.api.model.Action;
import vahy.api.model.State;
import vahy.api.model.observation.Observation;
import vahy.impl.benchmark.PolicyResults;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Objects;
import java.util.function.Function;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class Tuning<
        TAction extends Enum<TAction> &Action,
        TObservation extends Observation,
        TState extends State<TAction, TObservation, TState>,
        TStatistics extends EpisodeStatistics> {

    TuningConfig config;
    TuningParamConfig paramConfig;
    Function<TuningTrial, PolicyResults<TAction, TObservation, TState, TStatistics>> target;
    TuningParser parser;

    public Tuning(TuningConfig config, TuningParamConfig paramConfig, Function<TuningTrial, PolicyResults<TAction, TObservation, TState, TStatistics>> target) {
        this.config = config;
        this.paramConfig = paramConfig;
        this.target = target;
        this.parser = new TuningParser(paramConfig);
    }

    public TuningResult run() {
        TuningResult tuningResult = null;
        String environmentPath = "/usr/bin/python";
        String scriptName = "tuning.py";
        var scriptPath = Paths.get(Objects.requireNonNull(Tuning.class.getClassLoader().getResource(scriptName)).getPath());
        String workingDirPathName = scriptPath.toString().substring(0, scriptPath.toString().length() - scriptName.length()) + config.getName() + "/";
        String queueDirPathName = workingDirPathName + "trainable/";
        String solvedDirPathName = queueDirPathName + "solved/";

        File workingDirPathFile = new File(workingDirPathName);
        if (!workingDirPathFile.exists()) {
            workingDirPathFile.mkdir();
        }
        File queueDirPathFile = new File(queueDirPathName);
        if (!queueDirPathFile.exists()) {
            queueDirPathFile.mkdir();
        }
        File solvedDirPathFile = new File(solvedDirPathName);
        if (!solvedDirPathFile.exists()) {
            solvedDirPathFile.mkdir();
        }

        clearQueueDir(queueDirPathName);

        try {
            Process process = Runtime.getRuntime().exec(environmentPath + " " + scriptPath + " " + workingDirPathName);  // TODO: encode and pass tuning cfg + param cfg
            try(BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.defaultCharset()));
                BufferedReader error = new BufferedReader(new InputStreamReader(process.getErrorStream(), Charset.defaultCharset()))) {
                String line;
                String line2;

                Thread.sleep(8000); // wait some time for the python script to initialize

                new Thread(() -> {
                    while (true) {
                        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(queueDirPathName))) {
                            Path curDirPath = null;
                            for (var entry : stream) {
							    if (new File(entry.toString()).isDirectory() && !entry.toString().endsWith("solved")) {
                                    curDirPath = entry;
                                    break;
                                }
                            }
                            if (curDirPath == null) {
                                break;
                            }

                            String curDirName = curDirPath.getName(curDirPath.getNameCount() - 1).toString();
                            Path paramsFilePath = Paths.get(queueDirPathName + curDirName + "/params.json");
                            TuningTrial trial = parser.decodeTrial(Files.readString(paramsFilePath));
                            var trialResult = target.apply(trial);
                            String res = trialResult.getEvaluationStatistics().getTotalPayoffAverage().get(1).toString(); // TODO: parser.encodeResult(trialResult);

                            File result = new File(queueDirPathName + curDirName + "/solution.json");
                            PrintWriter f = new PrintWriter(result, StandardCharsets.UTF_8);
                            f.println(res);
                            f.close();

                            File target = new File(queueDirPathName + curDirName + "/result.json");
                            while (target.length() == 0 ) {
                                Thread.sleep(1000); // TODO: replace with ExecutorService
                            }
                            Files.move(Paths.get(queueDirPathName + curDirName), Paths.get(solvedDirPathName + curDirName), REPLACE_EXISTING);
                            Thread.sleep(1000);
                        } catch (IOException | InterruptedException ex) {
                            System.out.println("Oopa: " + ex.toString());
                            break; // TODO: handle exceptions
                        }
                    }
                }).start();

                while ((line = input.readLine()) != null) {
                    System.out.println(line);
                    if (line.contains("Best config")) {
                        tuningResult = null; // TODO: parser.decodeTuningResult(line);
                    }
                }
                while ((line2 = error.readLine()) != null) {
                    System.out.println(line2);
                }
            }
            var exitValue = process.waitFor();
            if(exitValue != 0) {
                throw new IllegalStateException("Python process ended with non-zero exit value. Exit val: [" + exitValue + "]");
            }
        } catch (IOException | InterruptedException e) {
            System.out.println("yikes");
            return null; // TODO: fix
        }

        return tuningResult;
    }

    private void clearQueueDir(String queueDir) {
        File queueDirFile = new File(queueDir);
        for (File f : Objects.requireNonNull(queueDirFile.listFiles())) {
            if (!f.getName().equals("solved")) {
                if (f.isDirectory()) {
                    clearQueueDir(queueDir + f.getName());
                }
                f.delete();
            }
        }
    }
}
