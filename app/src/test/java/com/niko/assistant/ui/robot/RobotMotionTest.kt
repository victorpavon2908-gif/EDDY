package com.niko.assistant.ui.robot

import org.junit.Assert.*
import org.junit.Test

class RobotMotionTest {
    @Test fun understandsVoseoAndPoliteCommands() {
        assertEquals(RobotMotion.DANCE, RobotMotion.parse("Leo, bailá para mí"))
        assertEquals(RobotMotion.JUMP, RobotMotion.parse("Leo, brincá, porfa"))
        assertEquals(RobotMotion.SPIN, RobotMotion.parse("Leo, date una vuelta"))
        assertEquals(RobotMotion.WAVE, RobotMotion.parse("Leo, mové los brazos"))
        assertEquals(RobotMotion.DANCE, RobotMotion.parse("Quiero que bailes"))
    }
    @Test fun neverStealsDictationSearchOrNegation() {
        listOf("mandá un mensaje a Ana que diga baila", "buscá cómo baila un robot", "Leo no bailes", "no quiero que gires", "abrí WhatsApp", "Leo, para").forEach {
            assertNull(it, RobotMotion.parse(it))
        }
    }
    private fun director() = RobotMotionDirector { if (it == "Jump") .7f else 3f }
    @Test fun jumpReturnsToCurrentActivity() {
        val d = director()
        d.setActivity(RobotActivity.SPEAKING, true, false)
        d.perform(RobotMotion.JUMP, 1.0)
        assertEquals("Jump", d.frame(1.1).clip)
        assertEquals("Talk", d.frame(1.8).clip)
    }
    @Test fun newListeningInterruptsDance() {
        val d = director()
        d.perform(RobotMotion.DANCE, 0.0)
        assertEquals("Dance", d.frame(.1).clip)
        d.setActivity(RobotActivity.LISTENING, true, false)
        assertEquals("Listen", d.frame(.2).clip)
        d.perform(RobotMotion.WAVE, .3)
        assertEquals("Listen", d.frame(.4).clip)
    }
    @Test fun transitionsBlendPreviousPoseThenReleaseIt() {
        val d = director()
        d.frame(0.0)
        d.setActivity(RobotActivity.SPEAKING, true, false)
        assertEquals("Idle", d.frame(1.0).previous)
        assertEquals(.5f, d.frame(1.14).blend, .01f)
        assertNull(d.frame(1.3).previous)
    }
    @Test fun stoppingAndDeactivationCancelMotion() {
        val d = director()
        d.perform(RobotMotion.SPIN, 0.0)
        d.frame(.1)
        d.cancelMotion()
        assertEquals("Idle", d.frame(.2).clip)
        d.perform(RobotMotion.DANCE, .3)
        d.setActivity(RobotActivity.SPEAKING, false, false)
        val frame = d.frame(.4)
        assertEquals("Idle", frame.clip)
        assertEquals(0f, frame.seconds, 0f)
        assertNull(frame.previous)
    }
    @Test fun reducedMotionFreezesBodyButDoesNotChangeVoiceState() {
        val d = director()
        d.setActivity(RobotActivity.SPEAKING, true, true)
        d.perform(RobotMotion.JUMP, .1)
        assertEquals("Idle", d.frame(.2).clip)
        assertEquals(0f, d.frame(4.0).seconds, 0f)
        d.setActivity(RobotActivity.SPEAKING, true, false)
        assertEquals("Talk", d.frame(4.1).clip)
    }
    @Test fun repeatedGestureRestartsInsteadOfBeingIgnored() {
        val d = director()
        d.perform(RobotMotion.JUMP, 0.0)
        d.frame(0.0)
        d.perform(RobotMotion.JUMP, .5)
        assertEquals(.1f, d.frame(.6).seconds, .001f)
        assertEquals("Jump", d.frame(.9).clip)
    }
    @Test fun stopIsAnEventEvenWhenTheGestureCameFromATap() {
        RobotMotionBus.clear()
        val before = RobotMotionBus.requests.value
        RobotMotionBus.clear()
        assertNotEquals(before, RobotMotionBus.requests.value)
        assertNull(RobotMotionBus.requests.value?.motion)
    }
}
