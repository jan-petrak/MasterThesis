package vahy.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vahy.api.benchmark.EpisodeStatisticsCalculator;
import vahy.api.episode.EpisodeResultsFactory;
import vahy.api.episode.InitialStateSupplier;
import vahy.api.experiment.ApproximatorConfig;
import vahy.api.experiment.CommonAlgorithmConfig;
import vahy.api.experiment.ProblemConfig;
import vahy.api.experiment.SystemConfig;
import vahy.api.model.Action;
import vahy.api.model.State;
import vahy.api.policy.PolicyRecord;
import vahy.impl.benchmark.EpisodeStatisticsBase;
import vahy.impl.benchmark.PolicyResults;
import vahy.impl.episode.DataPointGeneratorGeneric;
import vahy.impl.model.observation.DoubleVector;
import vahy.impl.runner.EpisodeWriter;
import vahy.impl.runner.EvaluationArguments;
import vahy.impl.runner.PolicyArguments;
import vahy.impl.runner.Runner;
import vahy.impl.runner.RunnerArguments;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.BiFunction;

public class RoundBuilder<TConfig extends ProblemConfig, TAction extends Enum<TAction> & Action, TState extends State<TAction, DoubleVector, TState>, TPolicyRecord extends PolicyRecord, TStatistics extends EpisodeStatisticsBase> {

    private static final Logger logger = LoggerFactory.getLogger(RoundBuilder.class);

    public static final long EVALUATION_SEED_SHIFT = 100_000;

    private String roundName;
    private String timestamp;
    private boolean dumpData;
    private TConfig problemConfig;
    private SystemConfig systemConfig;
    private CommonAlgorithmConfig commonAlgorithmConfig;

    private List<PolicyArguments<TAction, DoubleVector, TState, TPolicyRecord>> policyArgumentList;

    private EpisodeStatisticsCalculator<TAction, DoubleVector, TState, TPolicyRecord, TStatistics> statisticsCalculator;
    private EpisodeResultsFactory<TAction, DoubleVector, TState, TPolicyRecord> resultsFactory;

    private BiFunction<TConfig, SplittableRandom, InitialStateSupplier<TAction, DoubleVector, TState>> instanceInitializerFactory;


    private List<DataPointGeneratorGeneric<TStatistics>> additionalDataPointGeneratorList;

    public RoundBuilder<TConfig, TAction, TState, TPolicyRecord, TStatistics> setRoundName(String roundName) {
        this.roundName = roundName;
        return this;
    }

    public RoundBuilder<TConfig, TAction, TState, TPolicyRecord, TStatistics> setProblemConfig(TConfig problemConfig) {
        this.problemConfig = problemConfig;
        return this;
    }

    public RoundBuilder<TConfig, TAction, TState, TPolicyRecord, TStatistics> setSystemConfig(SystemConfig systemConfig) {
        this.systemConfig = systemConfig;
        return this;
    }

    public RoundBuilder<TConfig, TAction, TState, TPolicyRecord, TStatistics> setCommonAlgorithmConfig(CommonAlgorithmConfig commonAlgorithmConfig) {
        this.commonAlgorithmConfig = commonAlgorithmConfig;
        return this;
    }

    public RoundBuilder<TConfig, TAction, TState, TPolicyRecord, TStatistics> setPolicySupplierList(List<PolicyArguments<TAction, DoubleVector, TState, TPolicyRecord>> policyArgumentsList) {
        this.policyArgumentList = policyArgumentsList;
        return this;
    }

    public RoundBuilder<TConfig, TAction, TState, TPolicyRecord, TStatistics> setStatisticsCalculator(EpisodeStatisticsCalculator<TAction, DoubleVector, TState, TPolicyRecord, TStatistics> statisticsCalculator) {
        this.statisticsCalculator = statisticsCalculator;
        return this;
    }

    public RoundBuilder<TConfig, TAction, TState, TPolicyRecord, TStatistics> setResultsFactory(EpisodeResultsFactory<TAction, DoubleVector, TState, TPolicyRecord> resultsFactory) {
        this.resultsFactory = resultsFactory;
        return this;
    }

    public RoundBuilder<TConfig, TAction, TState, TPolicyRecord, TStatistics> setAdditionalDataPointGeneratorListSupplier(List<DataPointGeneratorGeneric<TStatistics>> additionalDataPointGeneratorList) {
        this.additionalDataPointGeneratorList = additionalDataPointGeneratorList;
        return this;
    }

    public RoundBuilder<TConfig, TAction, TState, TPolicyRecord, TStatistics> setProblemInstanceInitializerSupplier(BiFunction<TConfig, SplittableRandom, InitialStateSupplier<TAction, DoubleVector, TState>> instanceInitializerFactory) {
        this.instanceInitializerFactory = instanceInitializerFactory;
        return this;
    }

    private void finalizeSetup() {
        if(roundName == null) {
            throw new IllegalArgumentException("Missing RunName");
        }
        if(systemConfig == null) {
            throw new IllegalArgumentException("Missing systemConfig");
        }
        if(problemConfig == null) {
            throw new IllegalArgumentException("Missing problemConfig");
        }
        if(commonAlgorithmConfig == null) {
            throw new IllegalArgumentException("Missing commonAlgorithmConfig");
        }
        if(instanceInitializerFactory == null) {
            throw new IllegalArgumentException("Missing instanceInitializerFactory");
        }
        if(statisticsCalculator == null) {
            throw new IllegalArgumentException("Missing statisticsCalculator");
        }
        if(resultsFactory == null) {
            throw new IllegalArgumentException("Missing resultsFactory");
        }
        if(policyArgumentList == null) {
            throw new IllegalArgumentException("Missing policyArgumentList");
        }
        checkPolicyArgumentList(policyArgumentList);
        timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss"));
        logger.info("Finalized setup with timestamp [{}]", timestamp);
        dumpData = (systemConfig.dumpEvaluationData() || systemConfig.dumpTrainingData());

    }

    private void checkPolicyArgumentList(List<PolicyArguments<TAction, DoubleVector, TState, TPolicyRecord>> policyArgumentsList) {
        Set<Integer> policyIdSet = new HashSet<>();
        for (PolicyArguments<TAction, DoubleVector, TState, TPolicyRecord> entry : policyArgumentsList) {
            if(policyIdSet.contains(entry.getPolicyId())) {
                throw new IllegalStateException("Two or more policies have policy id: [" + entry.getPolicyId() + "]");
            } else {
                policyIdSet.add(entry.getPolicyId());
            }
        }
    }

    public PolicyResults<TAction, DoubleVector, TState, TPolicyRecord, TStatistics> execute() {
        finalizeSetup();
        var runner = new Runner<TConfig, TAction, DoubleVector, TState, TPolicyRecord, TStatistics>();
        try {
            var episodeWriter = dumpData ? new EpisodeWriter<TAction, DoubleVector, TState, TPolicyRecord>(problemConfig, commonAlgorithmConfig, systemConfig, timestamp, roundName) : null;
            var runnerArguments = buildRunnerArguments(episodeWriter);
            var evaluationArguments = buildEvaluationArguments(episodeWriter);
            return runner.run(runnerArguments, evaluationArguments);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private RunnerArguments<TConfig, TAction, DoubleVector, TState, TPolicyRecord, TStatistics> buildRunnerArguments(EpisodeWriter<TAction, DoubleVector, TState, TPolicyRecord> episodeWriter) {
        final var finalRandomSeed = systemConfig.getRandomSeed();
        final var masterRandom = new SplittableRandom(finalRandomSeed);
        return new RunnerArguments<>(
            roundName,
            problemConfig,
            systemConfig,
            commonAlgorithmConfig,
            instanceInitializerFactory.apply(problemConfig, masterRandom.split()),
            resultsFactory,
            statisticsCalculator,
            additionalDataPointGeneratorList,
            episodeWriter,
            policyArgumentList
        );
    }

    private EvaluationArguments<TConfig, TAction, DoubleVector, TState, TPolicyRecord, TStatistics> buildEvaluationArguments(EpisodeWriter<TAction, DoubleVector, TState, TPolicyRecord> episodeWriter) {
        final var finalRandomSeed = systemConfig.getRandomSeed();
        final var masterRandom = new SplittableRandom(finalRandomSeed + EVALUATION_SEED_SHIFT);
        return new EvaluationArguments<>(
            roundName,
            problemConfig,
            systemConfig,
            instanceInitializerFactory.apply(this.problemConfig, masterRandom.split()),
            resultsFactory,
            statisticsCalculator,
            episodeWriter
        );
    }

    public static byte[] loadTensorFlowModel(ApproximatorConfig approximatorConfig, SystemConfig systemConfig, int inputCount, int outputActionCount) throws IOException, InterruptedException {
        var modelName = "tfModel_" + LocalDateTime.now().atZone(ZoneOffset.UTC);
        modelName = modelName.replace(":", "_");
        Process process = Runtime.getRuntime().exec(systemConfig.getPythonVirtualEnvPath()
            + " " +
            Paths.get("PythonScripts", "tensorflow_models", approximatorConfig.getCreatingScript()) +
            " " +
            modelName +
            " " +
            inputCount +
            " " +
            outputActionCount +
            " " +
            Paths.get("PythonScripts", "generated_models") +
            " " +
            (int)systemConfig.getRandomSeed());

        try(BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader error = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            String line2;

            while ((line = input.readLine()) != null) {
                logger.info(line);
            }
            while ((line2 = error.readLine()) != null) {
                logger.error(line2);
            }
        }
        var exitValue = process.waitFor();
        if(exitValue != 0) {
            throw new IllegalStateException("Python process ended with non-zero exit value. Exit val: [" + exitValue + "]");
        }
        var dir = new File(Paths.get("PythonScripts", "generated_models").toString());
        Files.createDirectories(dir.toPath());
        return Files.readAllBytes(new File(dir, modelName + ".pb").toPath());
    }
}