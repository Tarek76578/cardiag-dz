package dz.cardiag.app.core

object VinValidator {
    private val forbidden = setOf('I','O','Q')
    fun normalize(vin: String): String = vin.trim().uppercase().replace(" ", "")
    fun isValid(vin: String): Boolean { val v=normalize(vin); return v.length==17 && v.all { it in 'A'..'Z' || it in '0'..'9' } && v.none { it in forbidden } }
}
