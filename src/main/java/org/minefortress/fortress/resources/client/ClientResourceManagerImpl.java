package org.minefortress.fortress.resources.client;

import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import org.minefortress.fortress.resources.ItemInfo;

import java.util.*;

public class ClientResourceManagerImpl implements ClientResourceManager {

    private final StackGroupsManager groupManager = new StackGroupsManager();

    @Override
    public Set<ItemGroup> getGroups() {
        return groupManager.getGroups();
    }

    @Override
    public boolean hasStacks(List<ItemStack> stacks) {
        return stacks
                .stream()
                .allMatch(it -> {
                    final var item = it.getItem();
                    final var group = groupManager.getGroup(item);
                    final var itemStack = groupManager.getStcksManager(group).getStack(item);
                    if(itemStack == null) return false;
                    return itemStack.getCount() >= it.getCount();
                });
    }

    @Override
    public boolean hasItems(List<ItemInfo> stacks) {
        return stacks
                .stream()
                .allMatch(it -> {
                    final var item = it.item();
                    final var group = groupManager.getGroup(item);
                    final var itemStack = groupManager.getStcksManager(group).getStack(item);
                    if(itemStack == null) return false;
                    return itemStack.getCount() >= it.amount();
                });
    }

    @Override
    public boolean hasItem(ItemInfo item) {
        final var group = groupManager.getGroup(item.item());
        final var manager = groupManager.getStcksManager(group);
        final var stack = manager.getStack(item.item());
        if(stack == null) return false;
        return stack.getCount() >= item.amount();
    }

    @Override
    public List<ItemStack> getStacks(ItemGroup group) {
        return groupManager.getStacksFromGroup(group);
    }

    @Override
    public void setItemAmount(Item item, int amount) {
        final var group = groupManager.getGroup(item);
        final var manager = groupManager.getStcksManager(group);
        manager.getStack(item).setCount(amount);
    }

    @Override
    public void reset() {
        groupManager.clear();
    }

    @Override
    public List<ItemStack> getAllStacks() {
        return groupManager.getGroups().stream().flatMap(it -> groupManager.getStacksFromGroup(it).stream()).toList();
    }

}