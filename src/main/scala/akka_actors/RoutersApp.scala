package akka_actors

import akka.actor.typed.{ActorSystem, Behavior, SupervisorStrategy}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{Behaviors, Routers}

sealed trait Command

case class DoLog(text: String) extends Command

case class DoBroadcastLog(text: String) extends Command

object Worker {


  def apply(): Behavior[Command] = Behaviors.setup { context =>
    context.log.info("Starting worker")

    Behaviors.receiveMessage {
      case DoLog(text) =>
        context.log.info("Got message {}", text)
        Behaviors.same
    }
  }
}

object PoolRouter {
  def apply(): Behavior[Command] = {
    Behaviors.setup { context =>
      val pool = Routers.pool(poolSize = 4) {
        // make sure the workers are restarted if they fail
        Behaviors.supervise(Worker()).onFailure[Exception](SupervisorStrategy.restart)
      }
      val router = context.spawn(pool, "worker-pool")

      (0 to 10).foreach { n =>
        router ! DoLog(s"msg $n")
      }

      val poolWithBroadcast = pool.withBroadcastPredicate(_.isInstanceOf[DoLog])
      val routerWithBroadcast = context.spawn(poolWithBroadcast, "pool-with-broadcast")

      Behaviors.receiveMessage {
        case DoLog(text) =>
          router ! DoLog(text)
          Behaviors.same
        //this will be sent to all 4 routees
        case DoBroadcastLog(text) =>
          routerWithBroadcast ! DoLog(s"broadcasted msg: $text")
          Behaviors.same
      }
    }
  }
}

object GroupRouter {
  private val serviceKey = ServiceKey[Command]("log-worker")
  def apply(): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      // this would likely happen elsewhere - if we create it locally we
      // can just as well use a pool
      val worker = ctx.spawn(Worker(), "worker")
      ctx.system.receptionist ! Receptionist.Register(serviceKey, worker)

      val group = Routers.group(serviceKey)
      val router = ctx.spawn(group, "worker-group")

      // the group router will stash messages until it sees the first listing of registered
      // services from the receptionist, so it is safe to send messages right away
      (0 to 12).foreach { n =>
        router ! DoLog(s"msg $n")
      }

      Behaviors.empty
    }
  }
}

object RoutersApp extends App {
  private val poolRouter: ActorSystem[Command] = ActorSystem(PoolRouter(), "PoolRouter")
  poolRouter ! DoBroadcastLog("this is broadcast")

  (0 to 15).foreach { msg =>
    poolRouter ! DoLog(s"message $msg")
  }

  private val groupRouter: ActorSystem[Command] = ActorSystem(GroupRouter(), "GroupRouter")
}
