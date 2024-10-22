/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.common.workers

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.io.Serializable
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ExecutorServiceAdapterTest {

    companion object {
        val executorService : ExecutorService = Executors.newSingleThreadExecutor()
    }

    @Before
    fun setUp() {
        Action.reset()
    }

    @Test
    fun singleActionTest() {
        ExecutorServiceAdapter(executorService).use {
            it.submit(Action::class.java, Parameters("foo", "foo"))
        }
        assertThat(Action.invocationCount.get()).isEqualTo(1)
    }

    @Test
    fun multipleActionTest() {
        ExecutorServiceAdapter(executorService).use {
            for (i in 1..5) {
                it.submit(Action::class.java, Parameters("foo", "foo"))
            }
        }
        assertThat(Action.invocationCount.get()).isEqualTo(5)
    }

    @Test
    fun multipleInvocationTest() {
        ExecutorServiceAdapter(executorService).use {
            for (i in 1..5) {
                for (j in 1..3) {
                    it.submit(Action::class.java, Parameters("foo", "foo"))
                }
            }
        }
        assertThat(Action.invocationCount.get()).isEqualTo(15)
    }

    @Test
    fun awaitTest() {
        with(ExecutorServiceAdapter(executorService)) {
            for (i in 1..4) {
                submit(Action::class.java, Parameters("foo", "foo"))
            }
            await()
        }
        assertThat(Action.invocationCount.get()).isEqualTo(4)
    }

    @Test(expected = NoSuchMethodException::class)
    fun notSuitableConstructor() {
        try {
            ExecutorServiceAdapter(executorService).use {
                it.submit(WrongAction::class.java, Parameters("foo", "foo"))
            }
        } catch (e: WorkerExecutorException) {
            assertThat(e.causes.size).isEqualTo(1)
            throw e.cause!!.cause!!
        }
    }

    @Test(expected = InvocationTargetException::class)
    fun badConstructorException() {
        try {
            ExecutorServiceAdapter(executorService).use {
                it.submit(BadConstructorAction::class.java, Parameters("foo", "foo"))
            }
        } catch (e: WorkerExecutorException) {
            assertThat(e.causes.size).isEqualTo(1)
            throw e.cause!!.cause!!
        }
    }

    @Test
    fun notAccessibleConstructor() {
        ExecutorServiceAdapter(executorService).use {
            it.submit(PrivateConstructorClass::class.java, "Foo")
        }
    }

    @Test(expected = IllegalStateException::class)
    fun wrappingTasksExecutionExceptions() {
        ExecutorServiceAdapter(executorService).use {
            for (i in 1..4) {
                it.submit(Action::class.java, Parameters("Foo", "Bar"))
            }
            for (i in 1..4) {
                it.submit(Action::class.java, Parameters("Foo", "Foo"))
            }
            try {
                it.await()
            } catch (e: WorkerExecutorException) {
                assertThat(e.causes.size).isEqualTo(4)
                assertThat(e.causes[0].message).contains("wrong parameters value")
                throw e.cause!!.cause!!
            }
        }
    }

    private class Action(val parameters: Parameters) : Runnable {

        companion object {
            val invocationCount = AtomicInteger()
            fun reset() = invocationCount.set(0)
            fun incrementCount() = invocationCount.incrementAndGet()
        }

        override fun run() {
            incrementCount()
            if (parameters.param0 != parameters.param1) {
                throw IllegalStateException("wrong parameters value")
            }
        }
    }

    // Action with no constructor accepting the parameter type.
    private class WrongAction : Runnable {
        override fun run() {}
    }

    private class BadConstructorAction(parameters: Parameters) : Runnable {
        init {
            throw RuntimeException("Exception in constructor test : " + parameters.param0)
        }

        override fun run() {
        }
    }

    private data class Parameters(val param0: String, val param1: String) : Serializable

    private class PrivateConstructorClass private constructor(val parameters: String) : Runnable {
        override fun run() {}
    }
}