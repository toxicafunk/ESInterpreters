import events.{EventStore, InMemoryEventStore}

package object server {

  implicit val eventLog: EventStore[String] = InMemoryEventStore.apply[String]

}
