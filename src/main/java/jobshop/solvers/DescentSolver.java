package jobshop.solvers;

import jobshop.Instance;
import jobshop.Result;
import jobshop.Solver;
import jobshop.encodings.ResourceOrder;
import jobshop.encodings.Task;

import java.util.ArrayList;
import java.util.List;

public class DescentSolver implements Solver {

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
        int best_res = best_sol.toSchedule().makespan();
        boolean amelioration = true;
        while ((deadline - System.currentTimeMillis() > 1) && amelioration) {
            List<Block> blocs = blocksOfCriticalPath(best_sol);
            int best_res_neighbor = Integer.MAX_VALUE ;
            ResourceOrder best_sol_neighbor = null ;
            for (Block b : blocs) {
                List<Swap> swaps = neighbors(b);
                for (Swap s : swaps) {
                    ResourceOrder sol = best_sol.copy();
                    s.applyOn(sol);
                    if ((sol.toSchedule() != null) && sol.toSchedule().makespan() < best_res_neighbor) {
                        best_res_neighbor = sol.toSchedule().makespan();
                        best_sol_neighbor = sol.copy();
                    }
                }
            }
            if (best_res_neighbor < best_res) {
                best_res = best_res_neighbor ;
                best_sol = best_sol_neighbor.copy() ;
            } else {
                amelioration = false;
            }
        }
        return(new Result(instance,best_sol.toSchedule(),Result.ExitCause.Blocked));
    }

    /** Returns a list of all blocks of the critical path. */
    List<Block> blocksOfCriticalPath(ResourceOrder order) {
        List<Task> criticalPath = order.toSchedule().criticalPath() ;
        List<Block> blocs = new ArrayList<Block>();
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
                    blocs.add(new Block(machine_precedente,debut,debut + nb_task - 1));
                }
                nb_task = 1;
                firstTask = new Task(t.job,t.task);
                machine_precedente = order.instance.machine(firstTask);
            }
            // Quand on a terminé le parcours
            if (nb_task >= 2) {
                int debut = taskReference(firstTask,machine_precedente,order);
                blocs.add(new Block(machine_precedente,debut,debut + nb_task - 1));
            }
        }
        return blocs ;
    }

    /** For a given block, return the possible swaps for the Nowicki and Smutnicki neighborhood */
    List<Swap> neighbors(Block block) {
        List<Swap> swaps = new ArrayList<Swap>() ;
        if ((block.lastTask - block.firstTask) == 1) {
            swaps.add(new Swap(block.machine, block.firstTask, block.lastTask));
        } else {
            swaps.add(new Swap(block.machine, block.firstTask, block.firstTask+1));
            swaps.add(new Swap(block.machine, block.lastTask-1, block.lastTask));
        }
        return swaps;
    }

}
