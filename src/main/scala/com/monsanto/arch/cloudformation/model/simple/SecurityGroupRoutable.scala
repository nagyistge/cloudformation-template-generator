package com.monsanto.arch.cloudformation.model.simple

import com.monsanto.arch.cloudformation.model.{Token, ResourceRef, Template}
import com.monsanto.arch.cloudformation.model.resource._
import Builders._

trait SecurityGroupRoutableMaker[R <: Resource[R]]{

  def withSG(r: R, sgr: ResourceRef[`AWS::EC2::SecurityGroup`]): R

  private def innerSecurityGroup(r: R)(implicit vpc: `AWS::EC2::VPC`) =
    securityGroupFromOption(r.name + resourceNameSafeUUID(), "Autogenerated", r.Condition)

  def from(r: R)(implicit vpc: `AWS::EC2::VPC`): SecurityGroupRoutable[R] = {
    val sg = innerSecurityGroup(r)
    SecurityGroupRoutable(withSG(r, ResourceRef(sg)), sg)
  }
}
object SecurityGroupRoutableMaker {
  implicit object EC2Maker extends SecurityGroupRoutableMaker[`AWS::EC2::Instance`] {
    def withSG(r: `AWS::EC2::Instance`, sgr: ResourceRef[`AWS::EC2::SecurityGroup`]) = r.copy(SecurityGroupIds = r.SecurityGroupIds :+ sgr)
  }

  implicit object ELBMaker extends SecurityGroupRoutableMaker[`AWS::ElasticLoadBalancing::LoadBalancer`] {
    def withSG(
      r: `AWS::ElasticLoadBalancing::LoadBalancer`,
      sgr: ResourceRef[`AWS::EC2::SecurityGroup`]
    ): `AWS::ElasticLoadBalancing::LoadBalancer` = {
      val sgs = r.SecurityGroups match {
        case Some(sg) => sg :+ Token.fromAny(sgr)
        case None     => Seq(Token.fromAny(sgr))
      }
      r.copy(SecurityGroups = Some(sgs))
    }
  }

  implicit object AutoScalingLaunchMaker extends SecurityGroupRoutableMaker[`AWS::AutoScaling::LaunchConfiguration`] {
    def withSG(r: `AWS::AutoScaling::LaunchConfiguration`, sgr: ResourceRef[`AWS::EC2::SecurityGroup`]) =
      r.copy(SecurityGroups = r.SecurityGroups :+ Token.fromAny(sgr))
  }
}

case class SecurityGroupRoutable[R <: Resource[R]](resource: R, sg: `AWS::EC2::SecurityGroup`, extras: Option[Seq[Resource[_]]] = None) {
  private[model] def resources: Seq[Resource[_]] = Seq(resource, sg) ++ extras.getOrElse( Seq.empty )
  def map[B](f: R => B): B = f(this.resource)
}
object SecurityGroupRoutable {
  def from[R <: Resource[R] : SecurityGroupRoutableMaker](r: R)(implicit vpc: `AWS::EC2::VPC`): SecurityGroupRoutable[R] =
    implicitly[SecurityGroupRoutableMaker[R]].from(r)

  def from(launchConfigSGR: SecurityGroupRoutable[`AWS::AutoScaling::LaunchConfiguration`], asg: `AWS::AutoScaling::AutoScalingGroup`) =
    SecurityGroupRoutable(asg, launchConfigSGR.sg, Some(Seq(launchConfigSGR.resource))) // TODO: this "extras" indirection is a bit trixie
}
