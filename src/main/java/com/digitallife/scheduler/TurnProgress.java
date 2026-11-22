package com.digitallife.scheduler;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 供界面查看正在处理和最近完成的回合。 */
@Component
public class TurnProgress {

    private static final int RECENT_LIMIT = 30;

    public enum Stage {
        QUEUED, PREPARING, THINKING, REPLIED, SILENT, NO_REPLY, UNAVAILABLE, FAILED;

        private boolean active() {
            return this == QUEUED || this == PREPARING || this == THINKING;
        }
    }

    public record Entry(String turnId, EventType triggerType, Long userMessageId, Stage stage,
                        Instant receivedAt, Instant startedAt, Instant updatedAt,
                        String innerThought, String reasoning) {}

    private final Map<String, Entry> turns = new LinkedHashMap<>();

    public synchronized void update(TriggerEvent event, Stage stage, String innerThought, String reasoning) {
        Instant now = Instant.now();
        Entry previous = turns.get(event.turnId());
        Instant startedAt = switch (stage) {
            case QUEUED -> null;
            case PREPARING -> now;
            default -> previous == null ? now : previous.startedAt();
        };
        turns.put(event.turnId(), new Entry(event.turnId(), event.type(), event.userMessageId(), stage,
                event.createdAt(), startedAt, now, innerThought, reasoning));

        if (!stage.active()) {
            long finished = turns.values().stream().filter(entry -> !entry.stage().active()).count();
            if (finished > RECENT_LIMIT) {
                var iterator = turns.values().iterator();
                while (iterator.hasNext()) {
                    if (!iterator.next().stage().active()) {
                        iterator.remove();
                        break;
                    }
                }
            }
        }
    }

    public void update(TriggerEvent event, Stage stage) {
        update(event, stage, null, null);
    }

    public synchronized List<Entry> snapshot() {
        return List.copyOf(turns.values());
    }
}
