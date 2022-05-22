package org.minefortress.entity.ai.goal;

import net.minecraft.entity.ai.goal.Goal;
import org.minefortress.entity.Colonist;
import org.minefortress.entity.ai.controls.TaskControl;
import org.minefortress.entity.ai.professions.BlacksmithDailyTask;
import org.minefortress.entity.ai.professions.CrafterDailyTask;
import org.minefortress.entity.ai.professions.ProfessionDailyTask;

import java.util.EnumSet;
import java.util.Map;

import static java.util.Map.entry;

public class DailyProfessionTasksGoal extends Goal {

    private final Colonist colonist;
    private final Map<String, ProfessionDailyTask> dailyTasks = Map.ofEntries(
            entry("crafter", new CrafterDailyTask()),
            entry("blacksmith", new BlacksmithDailyTask())
    );

    private ProfessionDailyTask currentTask;

    public DailyProfessionTasksGoal(Colonist colonist) {
        this.colonist = colonist;
        this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK, Goal.Control.JUMP));
    }

    @Override
    public boolean canStart() {
        final TaskControl taskControl = getTaskControl();
        if(taskControl.hasTask()) return false;
        final String professionId = colonist.getProfessionId();
        if(colonist.getFortressManager().isEmpty()) return false;
        if(!dailyTasks.containsKey(professionId)) return false;

        this.currentTask = dailyTasks.get(professionId);
        return this.currentTask.canStart(colonist);
    }

    @Override
    public void start() {
        colonist.getTaskControl().setDoingEverydayTasks(true);
        this.currentTask.start(colonist);
    }

    @Override
    public void tick() {
        this.currentTask.tick(colonist);
    }

    @Override
    public boolean shouldContinue() {
        return this.dailyTasks.containsKey(colonist.getProfessionId())
                && this.currentTask.shouldContinue(colonist)
                && !getTaskControl().hasTask();
    }

    @Override
    public void stop() {
        this.currentTask.stop(colonist);
        colonist.getTaskControl().setDoingEverydayTasks(false);
    }

    @Override
    public boolean canStop() {
        return false;
    }

    private TaskControl getTaskControl() {
        return colonist.getTaskControl();
    }
}
