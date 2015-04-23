/*
 * Copyright 2015 RONDHUIT Co.,LTD.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nlp4l.lm

abstract class HmmTracer {
}

case class Token(word: String, cls: String)

abstract case class AbstractNode(cls: Int, cost: Int, tcost: Int = Int.MaxValue){

  var backLink: AbstractNode = null
  var total: Int = tcost
  var nextSameEnd: AbstractNode = null

  def replaceTotalCostIfSmaller(leftNode: AbstractNode): Unit = {
    if(leftNode.total + cost < total){
      backLink = leftNode
      total = leftNode.total + cost
    }
  }
}
