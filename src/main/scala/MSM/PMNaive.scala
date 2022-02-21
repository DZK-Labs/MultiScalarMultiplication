package MSM

import chisel3._
import chisel3.internal.naming.chiselName
import chisel3.util.RegEnable


/** Point Multiplication Module (Naive)
 * This module performs scalar point multiplication. However, it does this
 * rather inefficiently. If we wanted to compute P * 12, this module does
 * P + P + P + P and so on. This faster way to do this, which would constrain
 * the latency to log2Ceil(scalar) cycles would be to use the double and add
 * method, and implementation for which can be found in PMBitSerial.
 * */
class PMNaive(pw: Int, sw: Int) extends Module {
  val io = IO(new Bundle {
    val a =  Input(SInt(pw.W))
    val p =  Input(SInt(pw.W))
    val px = Input(SInt(pw.W))
    val py = Input(SInt(pw.W))
    val s =  Input(SInt(sw.W))
    val load = Input(Bool())
    val valid = Output(Bool())
    val outx = Output(SInt(pw.W))
    val outy = Output(SInt(pw.W))
  })

  // valid bit that determines whether output is valid on start up
  val validBit = RegEnable(false.B, false.B, io.load)
  validBit := validBit || io.load

  // reg to deal w inf input
  val infinputreg = Reg(Bool())

  // regs to latch x, y, and s values, delay load by 1 cycle
  val x = RegEnable(io.px, 0.S, io.load)
  val y = RegEnable(io.py, 0.S, io.load)
  val s = RegEnable(io.s, 0.S, io.load)
  val delayedLoad = RegNext(io.load)

  // instantiate PointAddition module and make connections
  val padd = Module(new PointAddition(pw))
  padd.io.a := io.a
  padd.io.p := io.p
  padd.io.p1x := x
  padd.io.p1y := y

  padd.io.p2x := Mux(delayedLoad, x, padd.io.outx) // initial values? x else intermed results
  padd.io.p2y := Mux(delayedLoad, y, padd.io.outy)

  val infinput = padd.io.p2x === 0.S && padd.io.p2y === 0.S && validBit && !RegNext(padd.io.load) // last clasue very hacky...
  val test = infinput && !RegNext(infinput)

  // takes extra cycle to latch (x,y) or intermediate results
  padd.io.load := (delayedLoad || (RegNext(padd.io.valid) && !RegNext(delayedLoad)))

  when (infinput) {
    infinputreg := true.B
  }

  when (RegNext(RegNext(RegNext(infinputreg)))) {
    infinputreg := false.B
    padd.io.p2x := x
    padd.io.p2y := y
  }

  when (!infinputreg && RegNext(infinputreg)) {
    s := s - 1.S
  }


  // update inputs into padd module
  //when (test) {
  //  s := s - 2.S
  //  padd.io.p2x := x
  //  padd.io.p1x := y
  //}
  when (s > 0.S && padd.io.valid && !io.load && !delayedLoad && !infinputreg) {
    //printf("made it here\n")
    s := s - 1.S
    padd.io.p2x := padd.io.outx
    padd.io.p2y := padd.io.outy
  }

  // regs to hold result, assign outputs, assert valid
  val xres = RegEnable(padd.io.outx, 0.S, padd.io.valid)
  val yres = RegEnable(padd.io.outy, 0.S, padd.io.valid)
  when (infinput && s === 2.S) {
    printf("\n\nthis case!\n\n")
    io.outx := x
    io.outy := y
    io.valid := true.B
    s := 1.S
  } .otherwise {
    io.outx := xres
    io.outy := yres
    io.valid := s === 1.S && padd.io.valid && validBit
  }

  // handle special cases
  when (io.s === 1.S) {
    io.valid := true.B && validBit
    io.outx := io.px
    io.outy := io.py
  } .elsewhen (io.s === 0.S) {
    io.valid := true.B && validBit
    io.outx := 0.S
    io.outy := 0.S
  }
  // update padd inputs, decrement s
  //when (s > 1.S && padd.io.valid && !io.load && !delayedLoad && !infinputreg) {
  //  s := s - 1.S
  //  padd.io.p2x := padd.io.outx
  //  padd.io.p2y := padd.io.outy
  //}


  //printf(p"(${x},${y}) * ${s}\n")


  // debugging
  //when (x === 12.S && y === 16.S) {
    printf(p"1. regs(${x},${y}) p1(${padd.io.p1x},${padd.io.p1y}) p2(${padd.io.p2x},${padd.io.p2y}) infinput=${infinput} test=${test} infinputreg(${infinputreg})\n")
    printf(p"2. pmult s=${s} result=(${xres},${yres}), io.load=${io.load} padd.io.load=${padd.io.load} and padd.valid=${padd.io.valid} vb=${validBit}\n\n")
    printf("\n")
  //}
  //when (io.valid) {
  //  printf(p"out = (${io.outx}${io.outy})\n")
  //}
}

