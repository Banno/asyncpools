package org.pblue.asyncpools

import scala.util.{ Success, Failure }
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import akka.actor.{ ActorSystem, Props, OneForOneStrategy }
import akka.actor.SupervisorStrategy._
import akka.routing.RoundRobinRouter
import akka.pattern.ask
import akka.util.Timeout

class WorkerPool[T](
	name: String, 
	size: Int, 
	defaultTimeout: Timeout,
	maxNrOfRetries: Int,
	retryRange: Duration,
	objectFactory: PoolableObjectFactory[T])(implicit actorSystem: ActorSystem) {

	private val supervisor = 
		OneForOneStrategy(
			maxNrOfRetries = maxNrOfRetries, 
	    	withinTimeRange = retryRange) {

			case _: Throwable => Resume
		}

	private val router = 
		actorSystem.actorOf(
			props = 
				Props(classOf[Worker[T]], objectFactory)
					.withRouter(RoundRobinRouter(
						nrOfInstances = size,
						supervisorStrategy = supervisor)), 
			name = name)

	import actorSystem.dispatcher 

	def execute[U](fn: T => U)(implicit timeout: Timeout = defaultTimeout): Future[U] = 
		ask(router, Job[T, U](fn)).map {
			case Success(res: U) => res
			case Failure(t) => throw new AsyncPoolsException("AsyncPools execution error", t)
		}

}