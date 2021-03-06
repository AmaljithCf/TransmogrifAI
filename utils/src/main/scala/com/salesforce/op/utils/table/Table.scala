/*
 * Copyright (c) 2017, Salesforce.com, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.salesforce.op.utils.table

import com.twitter.algebird.{Monoid, Semigroup}
import enumeratum._


object Table {
  /**
   * Simple factory for creating table instance with rows of [[Product]] types
   *
   * @param columns non empty sequence of column names
   * @param rows    non empty sequence of rows
   * @param name    table name
   * @tparam T row type of [[Product]]
   */
  def apply[T <: Product](columns: Seq[String], rows: Seq[T], name: String = ""): Table = {
    require(columns.nonEmpty, "columns cannot be empty")
    require(rows.nonEmpty, "rows cannot be empty")
    require(columns.length == rows.head.productArity,
      s"columns length must match rows arity (${columns.length}!=${rows.head.productArity})")
    val rowVals = rows.map(_.productIterator.map(v => Option(v).map(_.toString).getOrElse("")).toSeq)
    new Table(columns, rowVals, name)
  }

  private implicit val max = Semigroup.from[Int](math.max)
  private implicit val monoid: Monoid[Array[Int]] = Monoid.arrayMonoid[Int]
}

/**
 * Simple table representation consisting of rows, i.e:
 *
 * +----------------------------------------+
 * |              Transactions              |
 * +----------------------------------------+
 * | date | amount | source       | status  |
 * +------+--------+--------------+---------+
 * | 1    | 4.95   | Cafe Venetia | Success |
 * | 2    | 12.65  | Sprout       | Success |
 * | 3    | 4.75   | Caltrain     | Pending |
 * +------+--------+--------------+---------+
 *
 * @param columns non empty sequence of column names
 * @param rows    non empty sequence of rows
 * @param name    table name
 */
class Table private(columns: Seq[String], rows: Seq[Seq[String]], name: String) {
  private def formatCell(v: String, size: Int, sep: String, fill: String): PartialFunction[Alignment, String] = {
    case Alignment.Left => v + fill * (size - v.length)
    case Alignment.Right => fill * (size - v.length) + v
    case Alignment.Center =>
      String.format("%-" + size + "s", String.format("%" + (v.length + (size - v.length) / 2) + "s", v))
  }

  private def formatRow(
    values: Iterable[String],
    cellSizes: Iterable[Int],
    alignment: String => Alignment,
    sep: String = "|",
    fill: String = " "
  ): String = {
    val formatted = values.zipWithIndex.zip(cellSizes).map { case ((v, i), size) =>
      formatCell(v, size, sep, fill)(alignment(columns(i)))
    }
    formatted.mkString(s"$sep$fill", s"$fill$sep$fill", s"$fill$sep")
  }

  private def sortColumns(ascending: Boolean): Table = {
    val (columnsSorted, indices) = columns.zipWithIndex.sortBy(_._1).unzip
    val rowsSorted = rows.map(row => row.zip(indices).sortBy(_._2).unzip._1)
    new Table(
      columns = if (ascending) columnsSorted else columnsSorted.reverse,
      rows = if (ascending) rowsSorted else rowsSorted.map(_.reverse),
      name = name
    )
  }

  /**
   * Sort table columns in alphabetical order
   */
  def sortColumnsAsc: Table = sortColumns(ascending = true)

  /**
   * Sort table columns in inverse alphabetical order
   */
  def sortColumnsDesc: Table = sortColumns(ascending = false)

  /**
   * Pretty print table
   *
   * @param nameAlignment          table name alignment
   * @param columnAlignments       column name & values alignment
   * @param defaultColumnAlignment default column name & values alignment
   * @return pretty printed table
   */
  def prettyString(
    nameAlignment: Alignment = Alignment.Center,
    columnAlignments: Map[String, Alignment] = Map.empty,
    defaultColumnAlignment: Alignment = Alignment.Left
  ): String = {
    val columnSizes = columns.map(c => math.max(1, c.length)).toArray
    val cellSizes = rows.map(_.map(_.length).toArray).foldLeft(columnSizes)(Table.monoid.plus)
    val bracket = formatRow(Seq.fill(cellSizes.length)(""), cellSizes, _ => Alignment.Left, sep = "+", fill = "-")
    val rowWidth = bracket.length - 4
    val cleanBracket = formatRow(Seq(""), Seq(rowWidth), _ => Alignment.Left, sep = "+", fill = "-")
    val maybeName = Option(name) match {
      case Some(n) if n.nonEmpty => Seq(cleanBracket, formatRow(Seq(name), Seq(rowWidth), _ => nameAlignment))
      case _ => Seq.empty
    }
    val alignment: String => Alignment = columnAlignments.getOrElse(_, defaultColumnAlignment)
    val columnsHeader = formatRow(columns, cellSizes, alignment)
    val formattedRows = rows.map(formatRow(_, cellSizes, alignment))

    (maybeName ++ Seq(cleanBracket, columnsHeader, bracket) ++ formattedRows :+ bracket).mkString("\n")
  }

  override def toString: String = prettyString()

}

sealed trait Alignment extends EnumEntry
object Alignment extends Enum[Alignment] {
  val values = findValues
  case object Left extends Alignment
  case object Right extends Alignment
  case object Center extends Alignment
}
