package com.niko.assistant.brain

/**
 * Alias de compatibilidad para instalaciones y código heredado.
 * La implementación activa desde LEO 0.12 es [LeoStructuredPlanner].
 */
@Deprecated("Use LeoStructuredPlanner", ReplaceWith("LeoStructuredPlanner"))
typealias NikoSemanticActionResolver = LeoStructuredPlanner
