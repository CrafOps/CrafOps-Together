package net.ppekkungz.crafops.interaction;

/**
 * Enum representing every supported player-to-player interaction type.
 *
 * Designed for future expansion — each type maps to its own behavior
 * module inside InteractionManager.
 *
 * Current:  HAND_HOLD
 * Planned:  DRAG, CARRY, PIGGYBACK, GUIDE, SOCIAL
 */
public enum InteractionType {

    /**
     * Initiator and target walk together.
     * Target is gently pulled when too far from initiator.
     */
    HAND_HOLD,

    /**
     * Initiator forcibly drags the target toward them.
     * (Not yet implemented — reserved for future use.)
     */
    DRAG,

    /**
     * Initiator carries the target on their back.
     * (Not yet implemented — reserved for future use.)
     */
    CARRY,

    /**
     * Target rides on the initiator's back.
     * (Not yet implemented — reserved for future use.)
     */
    PIGGYBACK,

    /**
     * Initiator guides target movement direction.
     * (Not yet implemented — reserved for future use.)
     */
    GUIDE,

    /**
     * Generic social gesture (wave, hug animation, etc.)
     * (Not yet implemented — reserved for future use.)
     */
    SOCIAL
}