package com.niko.assistant.testsupport

import org.junit.runners.model.FrameworkMethod
import org.robolectric.RobolectricTestRunner
import org.robolectric.internal.bytecode.InstrumentationConfiguration

/** A JNI library can only be loaded once per JVM; share real ORT across Robolectric sandboxes. */
class LeoNativeMemoryTestRunner(testClass: Class<*>) : RobolectricTestRunner(testClass) {
    override fun createClassLoaderConfig(method: FrameworkMethod): InstrumentationConfiguration =
        InstrumentationConfiguration.Builder(super.createClassLoaderConfig(method))
            .doNotAcquireClass("com.niko.assistant.memory.embedding.LeoOrtBridge")
            .build()
}
