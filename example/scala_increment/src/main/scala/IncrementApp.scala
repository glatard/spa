/* IncrementApp.scala */
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.input.PortableDataStream
import java.io.File
import java.io.DataInputStream
import com.ericbarnhill.niftijio.NiftiVolume
import com.ericbarnhill.niftijio.NiftiHeader

object IncrementApp {
  val usage = """
    Usage: increment bb_dir output_dir iterations [--delay] [--work_dir]
  """

  def readImg( filename:String, data:PortableDataStream )
    : Tuple2[String, NiftiVolume] = {
    val niftibytes = data.open()
    val volume = NiftiVolume.read(niftibytes, filename)
    niftibytes.close()
    return (new File(filename).getName(), volume)
  }

  def incrementData( filename:String, volume: NiftiVolume, sleep: Int )
    : Tuple2[String, NiftiVolume] = {


    for( i <- 0 to volume.data.sizeX - 1) {
      for( j <- 0 to volume.data.sizeY - 1) {
        for( k <- 0 to volume.data.sizeZ - 1) {
          for( l <- 0 to volume.data.dimension - 1 ) {
            volume.data.set(i, j, k, l, volume.data.get(i, j, k, l) + 1.0)
          }
        }
      }
    }
    Thread.sleep(sleep * 1000)
    return (filename, volume )
  }

  def saveData( fn: String, volume: NiftiVolume )
    : Tuple2[String, String] = {

    volume.write(fn)

    return (fn, "SUCCESS")
  }

  def main(args: Array[String]) {
    if (args.length == 0 || args.length < 3) { 
      println(usage)
      sys.exit(1)
    }
    val arglist = args.toList
    type OptionMap = Map[Symbol, Any]

    def nextArgument(map: OptionMap, list: List[String]) : OptionMap = {
      list match {
        case Nil => map
        case "--work_dir" :: string :: tail =>
                                nextArgument(map ++ Map('work_dir -> string), tail)
        case "--delay" :: value :: tail =>
                                nextArgument(map ++ Map('delay -> value.toInt), tail)
        case string :: tail if arglist.size - tail.size == 1  =>
                                nextArgument(map ++ Map('bb_dir -> string), tail)
        case string :: tail if arglist.size - tail.size == 2 =>
                                nextArgument(map ++ Map('output_dir -> string), tail)
        case string :: tail if arglist.size - tail.size == 3 =>
                                nextArgument(map ++ Map('iterations -> string.toInt), tail)
        case option :: tail => println("Unknown option "+option)
                               sys.exit(1)
      }
    }
    val options = nextArgument(Map(), arglist)
    
    val conf = new SparkConf().setAppName("Scala incrementation")
    val sc = new SparkContext(conf)

    val delay = if ((options get 'delay).isEmpty) 0 else (options get 'delay).get
    val of = new File(options('output_dir).asInstanceOf[String])
    val output_dir = of.getAbsolutePath()
    val its = options('iterations).asInstanceOf[Int]
    of.mkdirs()
    println(output_dir)

    var imRDD = sc.binaryFiles(options('bb_dir).asInstanceOf[String])
                  .map(x => readImg(x._1, x._2))

    for ( i <- 1 to its ) {
      imRDD = imRDD.map(x => incrementData(x._1, x._2, delay.asInstanceOf[Int]))
    }

    val result = imRDD.map( x => saveData(new File(output_dir, x._1).getAbsolutePath(), x._2) ).collect()

    result.foreach(x => println(x._1 + ": " + x._2))                              

    sc.stop()
  }
}
