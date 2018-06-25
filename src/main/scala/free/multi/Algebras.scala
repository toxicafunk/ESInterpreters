package free.multi

import cats.data.EitherK
import free.multi.algebras.{EvenSourcingAlgebra, MessagingAlgebra, OrdersAlgebra}

object Algebras {
  type MessagingAndOrdersAlg[T] = EitherK[MessagingAlgebra, OrdersAlgebra, T]

  type MessagingAndOrdersAndESAlg[T] = EitherK[EvenSourcingAlgebra, MessagingAndOrdersAlg, T]
}

