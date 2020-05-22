package jobshop.solvers;

import jobshop.Instance;
import jobshop.Result;
import jobshop.Solver;
import jobshop.encodings.ResourceOrder;
import jobshop.encodings.Task;

import java.util.ArrayList;
import java.util.List;

public class TabooSolver implements Solver {

    /** A block represents a subsequence of the critical path such that all tasks in it execute on the same machine.
     * This class identifies a block in a ResourceOrder representation.
     *
     * Consider the solution in ResourceOrder representation
     * machine 0 : (0,1) (1,2) (2,2)
     * machine 1 : (0,2) (2,1) (1,1)
     * machine 2 : ...
     *
     * The block with : machine = 1, firstTask= 0 and lastTask = 1
     * Represent the task sequence : [(0,2) (2,1)]
     *
     * */
    static class Block {
        /** machine on which the block is identified */
        final int machine;
        /** index of the first task of the block */
        final int firstTask;
        /** index of the last task of the block */
        final int lastTask;

        Block(int machine, int firstTask, int lastTask) {
            this.machine = machine;
            this.firstTask = firstTask;
            this.lastTask = lastTask;
        }

        public String toString() {
            return ("Machine " + this.machine + " | FTask : " + this.firstTask + " | LTask : " + this.lastTask);
        }
    }

    /**
     * Represents a swap of two tasks on the same machine in a ResourceOrder encoding.
     *
     * Consider the solution in ResourceOrder representation
     * machine 0 : (0,1) (1,2) (2,2)
     * machine 1 : (0,2) (2,1) (1,1)
     * machine 2 : ...
     *
     * The swam with : machine = 1, t1= 0 and t2 = 1
     * Represent inversion of the two tasks : (0,2) and (2,1)
     * Applying this swap on the above resource order should result in the following one :
     * machine 0 : (0,1) (1,2) (2,2)
     * machine 1 : (2,1) (0,2) (1,1)
     * machine 2 : ...
     */
    static class Swap {
        // machine on which to perform the swap
        final int machine;
        // index of one task to be swapped
        final int t1;
        // index of the other task to be swapped
        final int t2;

        Swap(int machine, int t1, int t2) {
            this.machine = machine;
            this.t1 = t1;
            this.t2 = t2;
        }

        /** Apply this swap on the given resource order, transforming it into a new solution. */
        public void applyOn(ResourceOrder order) {
            Task aux = order.tasksByMachine[machine][t1];

            order.tasksByMachine[machine][t1] = order.tasksByMachine[machine][t2] ;
            order.tasksByMachine[machine][t2] = aux;
        }
    }

    private static int taskReference(Task t, int m, ResourceOrder order){
        for(int i = 0; i < order.tasksByMachine[m].length; i++){
            if(t.equals(order.tasksByMachine[m][i])){
                return i;
            }
        }
        return -1;
    }

    @Override
    public Result solve(Instance instance, long deadline) {
        // initialisation with GreedySolver
        ResourceOrder best_sol = new ResourceOrder(new GreedySolver(GreedySolver.modes.EST_LRPT).solve(instance, deadline).schedule);
        ResourceOrder sol_cour = best_sol.copy();
        int sTaboo[][] = new int[instance.numMachines*instance.numJobs][instance.numMachines*instance.numJobs];
        int best_res = best_sol.toSchedule().makespan();
        Swap swapMem = new Swap(-1,-1,-1);
        int k = 0;
        int kMax = 1000;
        int dureeTaboo = 10;
        while ((deadline - System.currentTimeMillis() > 1) && k<=kMax) {
            List<TabooSolver.Block> blocs = blocksOfCriticalPath(sol_cour);
            int best_res_neighbor = Integer.MAX_VALUE ;
            ResourceOrder best_sol_neighbor = null ;
            for (TabooSolver.Block b : blocs) {
                List<TabooSolver.Swap> swaps = neighbors(b);
                for (TabooSolver.Swap s : swaps) {
                    ResourceOrder sol = sol_cour.copy();
                    s.applyOn(sol);
                    int Oun = (s.machine*instance.numJobs)+s.t1;
                    int Odeux = (s.machine*instance.numJobs)+s.t2;
                    if (sTaboo[Oun][Odeux] == 0 || k >= sTaboo[Oun][Odeux]) {
                        if ((sol.toSchedule() != null) && sol.toSchedule().makespan() < best_res_neighbor) {
                            best_res_neighbor = sol.toSchedule().makespan();
                            best_sol_neighbor = sol.copy();
                            swapMem = new Swap(s.machine, s.t1, s.t2);
                        }
                    }
                }
            }
            sol_cour = best_sol_neighbor.copy();
            int Oun = (swapMem.machine*instance.numJobs)+swapMem.t1;
            int Odeux = (swapMem.machine*instance.numJobs)+swapMem.t2;
            sTaboo[Odeux][Oun] = k + dureeTaboo;
            if (best_res_neighbor < best_res) {
                best_res = best_res_neighbor ;
                best_sol = best_sol_neighbor.copy() ;
            }
            k++;
        }
        return(new Result(instance,best_sol.toSchedule(),Result.ExitCause.Blocked));
    }

    /** Returns a list of all blocks of the critical path. */
    List<TabooSolver.Block> blocksOfCriticalPath(ResourceOrder order) {
        List<Task> criticalPath = order.toSchedule().criticalPath() ;
        List<TabooSolver.Block> blocs = new ArrayList<TabooSolver.Block>();
        int nb_task = 1;
        Task firstTask = criticalPath.get(0);
        int machine_precedente = order.instance.machine(firstTask);
        for (int i=1 ; i < criticalPath.size() ; i++) {
            Task t = criticalPath.get(i);
            int machine = order.instance.machine(t);
            if (machine_precedente == machine) {
                nb_task++;
            } else {
                if (nb_task >= 2) {
                    int debut = taskReference(firstTask,machine_precedente,order);
                    blocs.add(new TabooSolver.Block(machine_precedente,debut,debut + nb_task - 1));
                }
                nb_task = 1;
                firstTask = new Task(t.job,t.task);
                machine_precedente = order.instance.machine(firstTask);
            }
            // Quand on a terminé le parcours
            if (nb_task >= 2) {
                int debut = taskReference(firstTask,machine_precedente,order);
                blocs.add(new TabooSolver.Block(machine_precedente,debut,debut + nb_task - 1));
            }
        }
        return blocs ;
    }

    /** For a given block, return the possible swaps for the Nowicki and Smutnicki neighborhood */
    List<TabooSolver.Swap> neighbors(TabooSolver.Block block) {
        List<TabooSolver.Swap> swaps = new ArrayList<TabooSolver.Swap>() ;
        if ((block.lastTask - block.firstTask) == 1) {
            swaps.add(new TabooSolver.Swap(block.machine, block.firstTask, block.lastTask));
        } else {
            swaps.add(new TabooSolver.Swap(block.machine, block.firstTask, block.firstTask+1));
            swaps.add(new TabooSolver.Swap(block.machine, block.lastTask-1, block.lastTask));
        }
        return swaps;
    }

}
