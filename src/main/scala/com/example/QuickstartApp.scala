package com.example

import akka.Done
import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route

import scala.util.Failure
import scala.util.Success
import scala.concurrent.duration._

//#main-class
object QuickstartApp {
  //#start-http-server
  private def startHttpServer(routes: Route, system: ActorSystem[_]): Unit = {
    // Akka HTTP still needs a classic ActorSystem to start
    implicit val classicSystem: akka.actor.ActorSystem = system.toClassic
    import system.executionContext

    val futureBinding = Http().bindAndHandle(routes, "localhost", 8080)
    futureBinding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
    val cs = CoordinatedShutdown(classicSystem)
    cs.addTask(CoordinatedShutdown.PhaseServiceUnbind, "akka-http-server-unbind") { () =>
      futureBinding.flatMap { binding => 
        binding.terminate(5.seconds)
      }.map(_ => Done)
    }
    //cs.addTask(CoordinatedShutdown.PhaseServiceRequestsDone, "akka-http-requests-done") { () =>
    //  akka.pattern.after(2.seconds, system.toClassic.scheduler)(
    //  scala.concurrent.Future.successful(Done)
    //)
    //}
  }
  //#start-http-server
  def main(args: Array[String]): Unit = {
    //#server-bootstrapping
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      val userRegistryActor = context.spawn(UserRegistry(), "UserRegistryActor")
      context.watch(userRegistryActor)

      val routes = new UserRoutes(userRegistryActor)(context.system)
      startHttpServer(routes.userRoutes, context.system)

      Behaviors.empty
    }
    val system = ActorSystem[Nothing](rootBehavior, "HelloAkkaHttpServer")
    //#server-bootstrapping
  }
}
//#main-class
