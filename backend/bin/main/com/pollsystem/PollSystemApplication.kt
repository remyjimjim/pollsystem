package com.pollsystem

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class PollSystemApplication

fun main(args: Array<String>) {
    runApplication<PollSystemApplication>(*args)
}
