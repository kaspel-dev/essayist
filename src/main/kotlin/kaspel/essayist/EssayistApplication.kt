package kaspel.essayist

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(EssayistProperties::class)
class EssayistApplication

fun main(args: Array<String>) {
    runApplication<EssayistApplication>(*args)
}
