package akka_actors

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka_actors.Greeter.Greet
import akka_actors.Greeter.Greeted
import org.scalatest.wordspec.AnyWordSpecLike

class AkkaQuickstartSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  "A Greeter" must {
    //#test
    "reply to greeted" in {
      val replyProbe = createTestProbe[Greeted]()
      val underTest = spawn(Greeter())
      underTest ! Greet("Santa", replyProbe.ref)
      replyProbe.expectMessage(Greeted("Santa", underTest.ref))
    }
    //#test
  }
}
