package net.ppekkungz.crafops.interaction;

import java.util.*;

/** Pure topology operations, kept independent from Minecraft for deterministic tests. */
public final class ChainTopology {
    public static <T> List<T> merge(List<T> first, T firstEndpoint, List<T> second, T secondEndpoint, int maximum) {
        if (first.isEmpty() || second.isEmpty()) throw new IllegalArgumentException("Chains cannot be empty");
        if (!isEndpoint(first, firstEndpoint) || !isEndpoint(second, secondEndpoint)) throw new IllegalArgumentException("Only endpoints can be merged");
        Set<T> unique = new HashSet<>(first);
        for (T value : second) if (!unique.add(value)) throw new IllegalArgumentException("A merge cannot create a cycle");
        if (unique.size() > maximum) throw new IllegalArgumentException("Maximum chain size exceeded");
        List<T> result = new ArrayList<>(first);
        if (Objects.equals(result.get(0), firstEndpoint)) Collections.reverse(result);
        List<T> tail = new ArrayList<>(second);
        if (Objects.equals(tail.get(tail.size() - 1), secondEndpoint)) Collections.reverse(tail);
        result.addAll(tail);
        return result;
    }
    public static <T> boolean isEndpoint(List<T> chain, T value) {
        return !chain.isEmpty() && (Objects.equals(chain.get(0), value) || Objects.equals(chain.get(chain.size() - 1), value));
    }
    public static <T> List<List<T>> splitLink(List<T> chain, int linkIndex) {
        if (linkIndex < 0 || linkIndex >= chain.size() - 1) throw new IndexOutOfBoundsException(linkIndex);
        return List.of(new ArrayList<>(chain.subList(0, linkIndex + 1)), new ArrayList<>(chain.subList(linkIndex + 1, chain.size())));
    }
    public static <T> List<List<T>> removePlayer(List<T> chain, T player) {
        int index = chain.indexOf(player);
        if (index < 0) return List.of(new ArrayList<>(chain));
        return List.of(new ArrayList<>(chain.subList(0, index)), new ArrayList<>(chain.subList(index + 1, chain.size())));
    }
    private ChainTopology() {}
}
