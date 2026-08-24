package dz.cardiag.app.core

data class VinDecoded(val vin:String,val valid:Boolean,val wmi:String,val region:String?,val modelYear:Int?,val serial:String?,val checkDigitValid:Boolean)

object VinDecoder {
 private val regionMap=mapOf('A' to "Afrique",'B' to "Afrique",'C' to "Afrique",'J' to "Asie",'K' to "Asie",'L' to "Asie",'M' to "Asie",'S' to "Europe",'T' to "Europe",'V' to "Europe",'W' to "Europe",'X' to "Europe",'Y' to "Europe",'Z' to "Europe",'1' to "Amérique du Nord",'2' to "Amérique du Nord",'3' to "Amérique du Nord",'4' to "Amérique du Nord",'5' to "Amérique du Nord",'6' to "Océanie",'7' to "Océanie",'8' to "Amérique du Sud",'9' to "Amérique du Sud")
 private val transliteration=mapOf('A' to 1,'B' to 2,'C' to 3,'D' to 4,'E' to 5,'F' to 6,'G' to 7,'H' to 8,'J' to 1,'K' to 2,'L' to 3,'M' to 4,'N' to 5,'P' to 7,'R' to 9,'S' to 2,'T' to 3,'U' to 4,'V' to 5,'W' to 6,'X' to 7,'Y' to 8,'Z' to 9)
 private val weights=intArrayOf(8,7,6,5,4,3,2,10,0,9,8,7,6,5,4,3,2)
 fun normalize(vin:String)=vin.trim().uppercase().replace("-","").replace(" ","")
 fun decode(vin:String):VinDecoded{val v=normalize(vin);return VinDecoded(v,VinValidator.isValid(v),v.take(3),regionMap[v.firstOrNull()],v.getOrNull(9)?.let(::decodeYear),v.drop(11).takeIf{it.isNotBlank()},checkDigit(v))}
 private fun value(c:Char):Int=when(c){in '0'..'9'->c-'0';else->transliteration[c]?:0}
 private fun checkDigit(v:String):Boolean{if(v.length!=17)return false;val sum=v.indices.sumOf{value(v[it])*weights[it]};val expected=if(sum%11==10)'X' else ('0'+sum%11);return v[8]==expected}
 private fun decodeYear(c:Char):Int?=when(c){'A'->2010;'B'->2011;'C'->2012;'D'->2013;'E'->2014;'F'->2015;'G'->2016;'H'->2017;'J'->2018;'K'->2019;'L'->2020;'M'->2021;'N'->2022;'P'->2023;'R'->2024;'S'->2025;'T'->2026;'V'->2027;'W'->2028;'X'->2029;'Y'->2030;'1'->2031;'2'->2032;'3'->2033;'4'->2034;'5'->2035;'6'->2036;'7'->2037;'8'->2038;'9'->2039;else->null}
}
