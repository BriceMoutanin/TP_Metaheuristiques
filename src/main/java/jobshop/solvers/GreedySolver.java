package jobshop.solvers;

import jobshop.Instance;
import jobshop.Result;
import jobshop.Schedule;
import jobshop.Solver;
import jobshop.encodings.JobNumbers;
import jobshop.encodings.ResourceOrder;
import jobshop.encodings.Task;

import java.util.Arrays;
import java.util.function.ToDoubleBiFunction;

public class GreedySolver implements Solver {

    public enum modes {
        SPT,
        LPT,
        SRPT,
        LRPT,
        EST_SPT,
        EST_LRPT;
    }

    public modes mode ;

    public GreedySolver(modes mode) {
        this.mode = mode ;
    }

    public boolean en_cours(Instance instance, int[] taskPerJob) {
        boolean res = false;
        for (int i=0;i<taskPerJob.length;i++){
            if (taskPerJob[i] < instance.numTasks) {
                res = true ;
            }
        }
        return res;
    }

    public int remainingTime(Instance instance, int job, int next_task) {
        int res = 0 ;
        for (int t=next_task ; t < instance.numTasks ; t++) {
            res += instance.duration(job,t);
        }
        return res;
    }

    @Override
    public Result solve(Instance instance, long deadline) {

        ResourceOrder ro = new ResourceOrder(instance);
        // taskPerJob indique quelle est la prochaine tache a realiser pour chaque job
        int[] taskPerJob = new int[instance.numJobs] ;

        if (this.mode == modes.SPT) {
            while (en_cours(instance, taskPerJob)) {
                int duree_min = Integer.MAX_VALUE ;
                int job = -1 ;
                for (int j=0; j< taskPerJob.length; j++) {
                    if (taskPerJob[j] < instance.numTasks) { // Si on n'est pas encore arrivé à la dernière tache du job
                        int duree_tache = instance.duration(j, taskPerJob[j]);
                        if (duree_tache < duree_min) {
                            duree_min = duree_tache;
                            job = j;
                        }
                    }
                }
                int tache = taskPerJob[job];
                int machine = instance.machine(job, tache);
                ro.tasksByMachine[machine][ro.nextFreeSlot[machine]] = new Task(job, tache);
                ro.nextFreeSlot[machine]++;
                taskPerJob[job]++;
            }
        }
        else if (this.mode == modes.LPT) {
            while (en_cours(instance, taskPerJob)) {
                int duree_max = Integer.MIN_VALUE ;
                int job = -1 ;
                for (int j=0; j< taskPerJob.length; j++) {
                    if (taskPerJob[j] < instance.numTasks) { // Si on n'est pas encore arrivé à la dernière tache du job
                        int duree_tache = instance.duration(j, taskPerJob[j]);
                        if (duree_tache > duree_max) {
                            duree_max = duree_tache;
                            job = j;
                        }
                    }
                }
                int tache = taskPerJob[job];
                int machine = instance.machine(job, tache);
                ro.tasksByMachine[machine][ro.nextFreeSlot[machine]++] = new Task(job, tache);
                taskPerJob[job]++;
            }
        }
        else if (this.mode == modes.SRPT) {
            while (en_cours(instance, taskPerJob)) {
                int duree_min = Integer.MAX_VALUE ;
                int job = -1 ;
                for (int j=0; j< taskPerJob.length; j++) {
                    if (taskPerJob[j] < instance.numTasks) { // Si on n'est pas encore arrivé à la dernière tache du job
                        int duree_job_restante = remainingTime(instance, j, taskPerJob[j]);
                        if (duree_job_restante < duree_min) {
                            duree_min = duree_job_restante;
                            job = j;
                        }
                    }
                }
                int tache = taskPerJob[job];
                int machine = instance.machine(job, tache);
                ro.tasksByMachine[machine][ro.nextFreeSlot[machine]++] = new Task(job, tache);
                taskPerJob[job]++;
            }
        }
        else if (this.mode == modes.LRPT) {
            while (en_cours(instance, taskPerJob)) {
                int duree_max = Integer.MIN_VALUE ;
                int job = -1 ;
                for (int j=0; j< taskPerJob.length; j++) {
                    if (taskPerJob[j] < instance.numTasks) { // Si on n'est pas encore arrivé à la dernière tache du job
                        int duree_job_restante = remainingTime(instance, j, taskPerJob[j]);
                        if (duree_job_restante > duree_max) {
                            duree_max = duree_job_restante;
                            job = j;
                        }
                    }
                }
                int tache = taskPerJob[job];
                int machine = instance.machine(job, tache);
                ro.tasksByMachine[machine][ro.nextFreeSlot[machine]++] = new Task(job, tache);
                taskPerJob[job]++;
            }
        }

        else if (this.mode == modes.EST_SPT) {
            int [][] startTimes = new int [instance.numJobs][instance.numTasks] ;
            int[] nextFreeTimeResource = new int[instance.numMachines];
            while (en_cours(instance, taskPerJob)) {
                // On va choisir la tache a executer par rapport à sa date de début et sa duree min
                int date_min = Integer.MAX_VALUE;
                int duree_min = Integer.MAX_VALUE ;
                int job = -1 ;
                // REMPLISSAGE DU STARTITMES POUR CHAQUE TACHE REALISABLE //////////////////////////////////////////
                for (int j=0; j< taskPerJob.length; j++) {
                    if (taskPerJob[j] < instance.numTasks) { // Si on n'est pas encore arrivé à la dernière tache du job
                        int task = taskPerJob[j];
                        int machine = instance.machine(j, task);
                        // earliest start time for this task
                        int est = task == 0 ? 0 : startTimes[j][task-1] + instance.duration(j, task-1);
                        est = Math.max(est, nextFreeTimeResource[machine]);
                        startTimes[j][task] = est;
                    }
                }
                ////////////////////////////////////////////////////////////////////////////////////////////////////
                for (int j=0; j< taskPerJob.length; j++) {
                    if (taskPerJob[j] < instance.numTasks) { // Si on n'est pas encore arrivé à la dernière tache du job
                        // Si la tache a une date de depart plus petite
                        if (startTimes[j][taskPerJob[j]] < date_min) {
                            job = j;
                            date_min = startTimes[j][taskPerJob[j]];
                            duree_min = (instance.duration(j, taskPerJob[j]));
                            // Si la tache a la meme date de depart que la tache deja trouvée
                        } else if (startTimes[j][taskPerJob[j]] == date_min) {
                            // Si la tache est plus courte que la tache deja trouvée
                            if ((instance.duration(j, taskPerJob[j])) < duree_min) {
                                job = j;
                                date_min = startTimes[j][taskPerJob[j]];
                                duree_min = (instance.duration(j, taskPerJob[j]));
                            }
                        }
                    }
                }
                nextFreeTimeResource[instance.machine(job, taskPerJob[job])] = startTimes[job][taskPerJob[job]] + instance.duration(job, taskPerJob[job]);
                int tache = taskPerJob[job];
                int machine = instance.machine(job, tache);
                ro.tasksByMachine[machine][ro.nextFreeSlot[machine]++] = new Task(job, tache);
                taskPerJob[job]++;
            }
        }
        else if (this.mode == modes.EST_LRPT) {
            int [][] startTimes = new int [instance.numJobs][instance.numTasks] ;
            int[] nextFreeTimeResource = new int[instance.numMachines];
            while (en_cours(instance, taskPerJob)) {
                // On va choisir la tache a executer par rapport à sa date de début et la duree de son job
                int date_min = Integer.MAX_VALUE;
                int duree_job_min = Integer.MAX_VALUE ;
                int job = -1 ;
                // REMPLISSAGE DU STARTITMES POUR CHAQUE TACHE REALISABLE //////////////////////////////////////////
                for (int j=0; j< taskPerJob.length; j++) {
                    if (taskPerJob[j] < instance.numTasks) { // Si on n'est pas encore arrivé à la dernière tache du job
                        int task = taskPerJob[j];
                        int machine = instance.machine(j, task);
                        // earliest start time for this task
                        int est = task == 0 ? 0 : startTimes[j][task-1] + instance.duration(j, task-1);
                        est = Math.max(est, nextFreeTimeResource[machine]);
                        startTimes[j][task] = est;
                    }
                }
                ////////////////////////////////////////////////////////////////////////////////////////////////////
                for (int j=0; j< taskPerJob.length; j++) {
                    if (taskPerJob[j] < instance.numTasks) { // Si on n'est pas encore arrivé à la dernière tache du job
                        // Si la tache a une date de depart plus petite
                        if (startTimes[j][taskPerJob[j]] < date_min) {
                            job = j;
                            date_min = startTimes[j][taskPerJob[j]];
                            duree_job_min = remainingTime(instance, j, taskPerJob[j]);
                            // Si la tache a la meme date de depart que la tache deja trouvée
                        } else if (startTimes[j][taskPerJob[j]] == date_min) {
                            // Si la tache appartient au job qui a la plus grande duree restante
                            if (remainingTime(instance, j, taskPerJob[j]) > duree_job_min) {
                                job = j;
                                date_min = startTimes[j][taskPerJob[j]];
                                duree_job_min = remainingTime(instance, j, taskPerJob[j]);
                            }
                        }
                    }
                }
                nextFreeTimeResource[instance.machine(job, taskPerJob[job])] = startTimes[job][taskPerJob[job]] + instance.duration(job, taskPerJob[job]);
                int tache = taskPerJob[job];
                int machine = instance.machine(job, tache);
                ro.tasksByMachine[machine][ro.nextFreeSlot[machine]++] = new Task(job, tache);
                taskPerJob[job]++;
            }
        }

        return new Result(instance, ro.toSchedule(), Result.ExitCause.Blocked);

    }

}
