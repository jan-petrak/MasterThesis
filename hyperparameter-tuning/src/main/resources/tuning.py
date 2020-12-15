from ray import tune
from os import path

import sys
import os
import time

def trainable(config, checkpoint_dir=None):
    global working_dir

    file_name_part = "flowOptimizerType=" + str(config["flowOptimizerType"]) + ",learningRate=" + str(round(config["learningRate"], 5))
    file_name_part2 = "flowOptimizerType=" + str(config["flowOptimizerType"]) + ",learningRate=" + str(round(config["learningRate"], 6))

    cur_dir_name = ""
    for x in os.listdir(working_dir + "trainable/"):
        if file_name_part in x or file_name_part2 in x:
            cur_dir_name = x

    while not path.exists(working_dir + "trainable/" + cur_dir_name + "/solution.json"):
        time.sleep(1)

    f = open(working_dir + "trainable/" + cur_dir_name + "/solution.json", "r")
    tune.report(score=float(f.readline()))
    f.close()


working_dir = sys.argv[1]
tuning_cfg = { "name" : "SH3_learningRate_flowOptimizerType",
               "samples" : 10 }                                #TODO: parse(sys.argv[2])
param_cfg = { }                                                 #TODO: parse(sys.argv[3])

analysis = tune.run(
    trainable,
    local_dir=working_dir,
    verbose=0,
    resources_per_trial={"cpu":4},
    config={"learningRate": tune.uniform(0.01, 1),
            "flowOptimizerType": tune.choice([0, 1, 2, 3, 4])},
    num_samples=10
)

print("Best config: ", analysis.get_best_config(metric="score", mode="max"))
