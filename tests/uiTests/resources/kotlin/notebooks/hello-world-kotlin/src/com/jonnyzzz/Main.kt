package com.jonnyzzz

class Main {
  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      println("Hello World!")
    }

    @JvmStatic
    fun greet(name: String): String {
      val greeting = "Hello, $name!"
      println(greeting)
      return greeting
    }
  }
}
