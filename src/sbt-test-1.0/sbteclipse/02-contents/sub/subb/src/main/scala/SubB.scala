object SubB {
  val logger = org.slf4j.LoggerFactory.getLogger("suba")
  def subB = SubA.subA + " and subB" 
}
