package mesosphere.marathon.api.v1

import mesosphere.mesos.TaskBuilder
import mesosphere.marathon.Protos
import scala.collection.mutable
import scala.collection.JavaConverters._
import org.hibernate.validator.constraints.NotEmpty
import mesosphere.marathon.state.MarathonState
import mesosphere.marathon.Protos.Constraint
import javax.validation.constraints.Pattern
import com.fasterxml.jackson.annotation.JsonIgnoreProperties


/**
 * @author Tobi Knaup
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class AppDefinition extends MarathonState[Protos.ServiceDefinition] {

  @NotEmpty
  var id: String = ""
  @NotEmpty
  var cmd: String = ""
  var env: Map[String, String] = Map.empty
  var instances: Int = 0
  var cpus: Double = 1.0
  var mem: Double = 128.0
  @Pattern(regexp="(^//cmd$)|(^/[^/].*$)|")
  var executor: String = ""


  var constraints: Set[(String, String, Option[String])] = Set()

  var uris: Seq[String] = Seq()
  var ports: Seq[Int] = Nil
  // Number of new tasks this app may spawn per second in response to
  // terminated tasks. This prevents frequently failing apps from spamming
  // the cluster.
  var taskRateLimit: Double = 1.0


  def toProto: Protos.ServiceDefinition = {
    val commandInfo = TaskBuilder.commandInfo(this, Seq())
    val cpusResource = TaskBuilder.scalarResource(AppDefinition.CPUS, cpus)
    val memResource = TaskBuilder.scalarResource(AppDefinition.MEM, mem)
    val cons = constraints.map(x => {
      val b = Constraint.newBuilder()
        b.setField(x._1)
        b.setOperator(Constraint.Operator.valueOf(x._2.toUpperCase))
        if (x._3.nonEmpty) b.setValue(x._3.get)
        b.build()
      }
    )

    Protos.ServiceDefinition.newBuilder
      .setId(id)
      .setCmd(commandInfo)
      .setInstances(instances)
      .addAllPorts(ports.map(_.asInstanceOf[Integer]).asJava)
      .setExecutor(executor)
      .setTaskRateLimit(taskRateLimit)
      .addAllConstraints(cons.asJava)
      .addResources(cpusResource)
      .addResources(memResource)
      .build
  }

  def mergeFromProto(proto: Protos.ServiceDefinition) {
    val envMap = mutable.HashMap.empty[String, String]

    id = proto.getId
    cmd = proto.getCmd.getValue
    executor = proto.getExecutor
    taskRateLimit = proto.getTaskRateLimit
    instances = proto.getInstances
    ports = proto.getPortsList.asScala.asInstanceOf[Seq[Int]]
    constraints = proto.getConstraintsList.asScala.map(
      x => (x.getField,
            x.getOperator.toString,
            if (x.getValue != null) Some(x.getValue) else None)
    ).toSet

    // Add command environment
    for (variable <- proto.getCmd.getEnvironment.getVariablesList.asScala) {
      envMap(variable.getName) = variable.getValue
    }
    env = envMap.toMap

    // Add URIs
    uris = proto.getCmd.getUrisList.asScala.map(_.getValue)

    // Add resources
    for (resource <- proto.getResourcesList.asScala) {
      val value = resource.getScalar.getValue
      resource.getName match {
        case AppDefinition.CPUS =>
          cpus = value
        case AppDefinition.MEM =>
          mem = value
      }
    }

    // Restore
  }

  def mergeFromProto(bytes: Array[Byte]) {
    val proto = Protos.ServiceDefinition.parseFrom(bytes)
    mergeFromProto(proto)
  }
}

object AppDefinition {
  val CPUS = "cpus"
  val MEM = "mem"
}
