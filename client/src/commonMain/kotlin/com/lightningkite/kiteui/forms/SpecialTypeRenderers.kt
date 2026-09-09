package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.Align
import com.lightningkite.services.data.Acceleration
import com.lightningkite.services.data.Area
import com.lightningkite.services.data.DataSize
import com.lightningkite.services.data.EmailAddress
import com.lightningkite.services.data.Energy
import com.lightningkite.services.data.Force
import com.lightningkite.services.data.GeoCoordinate
import com.lightningkite.services.data.Length
import com.lightningkite.services.data.Mass
import com.lightningkite.services.data.PhoneNumber
import com.lightningkite.services.data.Power
import com.lightningkite.services.data.Pressure
import com.lightningkite.services.data.RelativeTemperature
import com.lightningkite.services.data.Speed
import com.lightningkite.services.data.Temperature
import com.lightningkite.services.data.Volume
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.views.ElementWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.kiteui.views.l2.errorText
import com.lightningkite.kiteui.views.direct.icon
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.MutableReactiveValue
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.extensions.commaString
import com.lightningkite.services.data.Cents
import com.lightningkite.services.data.Cents.Companion.cents
import com.lightningkite.services.data.Cents.Companion.dollars
import com.lightningkite.services.data.toEmailAddress
import com.lightningkite.services.data.toPhoneNumber
import kotlin.collections.listOf
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.uuid.Uuid

// ===== UUID =====
// by Claude

public object UuidRenderer : Renderer<Uuid> {
    override val name: String = "Uuid"  // by Claude
    override fun form(context: RenderContext<Uuid>, value: MutableReactive<Uuid>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        row {
            expanding.textInput {
                content bind value.lens(
                    get = { it.toString() },
                    modify = { old, new ->
                        try {
                            Uuid.parse(new)
                        } catch (e: Exception) {
                            old
                        }
                    }
                )
            }
            button {
                icon(Icon.sync, "Regenerate")
                onClick { value set Uuid.random() }
            }
        }
    }

    override fun view(context: RenderContext<Uuid>, value: Reactive<Uuid>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        text { ::content { value().toString() } }
    }

    override fun columnWidth(context: RenderContext<Uuid>, module: FormModule): Double = 24.0
}

// ===== EmailAddress =====

public object EmailAddressRenderer : Renderer<EmailAddress> {
    override val name: String = "Email"  // by Claude
    override fun form(context: RenderContext<EmailAddress>, value: MutableReactive<EmailAddress>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        col {
            textInput {
                content bind value.lens(
                    get = { it.raw },
                    set = { it.toEmailAddress() }
                )
            }
            errorText()
        }
    }

    override fun view(context: RenderContext<EmailAddress>, value: Reactive<EmailAddress>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        externalLink {
            text {
                ::content { value().raw }
                wraps = false
                ellipsis = true
            }
            ::to { value().url }
        }
    }

    override fun cellView(context: RenderContext<EmailAddress>, value: Reactive<EmailAddress>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        text {
            ::content { value().raw }
            wraps = false
            ellipsis = true
        }
    }

    override fun columnWidth(context: RenderContext<EmailAddress>, module: FormModule): Double = 20.0
}

// ===== PhoneNumber =====

public object PhoneNumberRenderer : Renderer<PhoneNumber> {
    override val name: String = "Phone"  // by Claude
    override fun form(context: RenderContext<PhoneNumber>, value: MutableReactive<PhoneNumber>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        col {
            textInput {
                content bind value.lens(
                    get = { it.raw },
                    set = { it.toPhoneNumber() }
                )
            }
            errorText()
        }
    }

    override fun view(context: RenderContext<PhoneNumber>, value: Reactive<PhoneNumber>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        externalLink {
            text {
                ::content { value().raw }
                wraps = false
                ellipsis = true
            }
            ::to { value().url }
        }
    }

    override fun cellView(context: RenderContext<PhoneNumber>, value: Reactive<PhoneNumber>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        text {
            ::content { value().raw }
            wraps = false
            ellipsis = true
        }
    }

    override fun columnWidth(context: RenderContext<PhoneNumber>, module: FormModule): Double = 15.0
}

// ===== GeoCoordinate =====

public object GeoCoordinateRenderer : Renderer<GeoCoordinate> {
    override val name: String = "Coordinates"  // by Claude
    override fun form(context: RenderContext<GeoCoordinate>, value: MutableReactive<GeoCoordinate>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        row {
            expanding.fieldTheme.numberInput {
                hint = "Latitude"
                content bind value.lens(
                    get = { it.latitude },
                    modify = { old, new -> old.copy(latitude = new ?: 0.0) }
                )
            }
            expanding.fieldTheme.numberInput {
                hint = "Longitude"
                content bind value.lens(
                    get = { it.longitude },
                    modify = { old, new -> old.copy(longitude = new ?: 0.0) }
                )
            }
        }
    }

    override fun view(context: RenderContext<GeoCoordinate>, value: Reactive<GeoCoordinate>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        text { ::content { "${value().latitude}, ${value().longitude}" } }
    }

    override fun columnWidth(context: RenderContext<GeoCoordinate>, module: FormModule): Double = 20.0

    override fun labeledForm(
        context: RenderContext<GeoCoordinate>,
        value: MutableReactive<GeoCoordinate>,
        module: FormModule,
        label: String,
        description: String?
    ): ElementWriter.CanAddTheme.() -> Unit = {
        fieldWithoutBorder(label, description) { form(context, value, module)() }
    }

    override fun labeledView(
        context: RenderContext<GeoCoordinate>,
        value: Reactive<GeoCoordinate>,
        module: FormModule,
        label: String,
        description: String?
    ): ElementWriter.CanAddTheme.() -> Unit = {
        fieldWithoutBorder(label, description) { view(context, value, module)() }
    }
}

// ===== Physical-quantity value classes =====
//
// Each renderer edits the value through a user-switchable unit. When the form
// is created, the initial unit is auto-picked from `preferredUnits` so the
// displayed number lands in a comfortable range (typically [1, 1000)).
// Less-common alternative units (e.g. imperial) are still available in the
// dropdown but are not auto-picked. Views just delegate to T.toString(),
// which already chooses a magnitude-appropriate unit for types like Length
// and DataSize.

/**
 * A unit of measurement for a value class.
 *
 * Equality is based on [symbol] alone — two `ValueUnit`s with the same symbol
 * represent the same logical unit, even if defined in different lists with
 * different lambda instances. This is essential for `Select.bind` to match a
 * picked unit against the dropdown's option list.
 */
public class ValueUnit<T>(
    public val symbol: String,
    public val fromBase: (T) -> Double,
    public val toBase: (Double) -> T,
) {
    override fun equals(other: Any?): Boolean = this === other || (other is ValueUnit<*> && other.symbol == symbol)
    override fun hashCode(): Int = symbol.hashCode()
    override fun toString(): String = "ValueUnit($symbol)"
}

/**
 * Pick the largest physical unit whose displayed magnitude is still >= 1.
 *
 * That gives the most compact human-readable rendering without dropping below 1
 * (e.g. 1000 B → "1000 B", 2000 B → "1.95 KiB", 5e6 B → "4.77 MiB"). Falls back
 * to the smallest available physical unit when the value is sub-unit everywhere
 * (e.g. picometer-scale lengths), and to the canonical (first) unit at zero.
 */
public fun <T> pickByMagnitude(value: T, units: List<ValueUnit<T>>): ValueUnit<T> {
    if (units.size == 1) return units.first()
    val candidates = units.map { it to abs(it.fromBase(value)) }
    if (candidates.all { it.second < 1e-12 }) return units.first()
    candidates.filter { it.second >= 1.0 }
        .minByOrNull { it.second }
        ?.let { return it.first }
    return candidates.maxBy { it.second }.first
}

public abstract class NumericValueClassRenderer<T : Any>(
    public override val name: String,
    public val units: List<ValueUnit<T>>,
    public val preferredUnits: List<ValueUnit<T>> = units,
) : Renderer<T> {
    init {
        require(units.isNotEmpty()) { "$name renderer requires at least one unit" }
        require(preferredUnits.isNotEmpty()) { "$name renderer requires at least one preferred unit" }
    }

    public open fun pickUnit(value: T): ValueUnit<T> = pickByMagnitude(value, preferredUnits)

    /** Format a value-in-unit for display. Default: round to 3 significant digits, comma-grouped. */
    public open fun format(magnitude: Double): String = magnitude.toSignificantDigits(3).commaString()

    override fun form(context: RenderContext<T>, value: MutableReactive<T>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        val initialUnit = value.state.getOrNull()?.let { pickUnit(it) } ?: preferredUnits.first()
        val unitChoice: MutableReactiveValue<ValueUnit<T>> = Signal(initialUnit)
        val unitOptions = Constant(units)
        row {
            expanding.frame {
                reactive {
                    val unit = unitChoice()
                    clearChildren()
                    fieldTheme.numberInput {
                        align = Align.End
                        content bind value.lens(
                            get = { unit.fromBase(it) },
                            modify = { old, new -> if (new != null) unit.toBase(new) else old }
                        )
                    }
                }
            }
            fieldTheme.select {
                bind(unitChoice, unitOptions) { it.symbol }
            }
        }
    }

    override fun view(context: RenderContext<T>, value: Reactive<T>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        text {
            ::content {
                val v = value()
                val unit = pickUnit(v)
                "${format(unit.fromBase(v))} ${unit.symbol}"
            }
        }
    }

    override fun columnWidth(context: RenderContext<T>, module: FormModule): Double = 16.0
}

/**
 * Round to [digits] significant digits.
 * Examples: 5.123456 @ 3 → 5.12 ; 5000.0 @ 3 → 5000.0 ; 0.0001234 @ 3 → 0.000123
 */
private fun Double.toSignificantDigits(digits: Int): Double {
    if (!isFinite() || this == 0.0) return this
    val magnitude = floor(log10(abs(this))).toInt()
    val scale = 10.0.pow((digits - 1 - magnitude).toDouble())
    return round(this * scale) / scale
}

private val dataSizeBinaryUnits: List<ValueUnit<DataSize>> = listOf(
    ValueUnit("B", { it.bytes.toDouble() }, { DataSize(it.toLong()) }),
    ValueUnit("KiB", { it.kibibytes }, { DataSize((it * 1024.0).toLong()) }),
    ValueUnit("MiB", { it.mebibytes }, { DataSize((it * 1024.0 * 1024.0).toLong()) }),
    ValueUnit("GiB", { it.gibibytes }, { DataSize((it * 1024.0 * 1024.0 * 1024.0).toLong()) }),
    ValueUnit("TiB", { it.tebibytes }, { DataSize((it * 1024.0 * 1024.0 * 1024.0 * 1024.0).toLong()) }),
)
private val dataSizeDecimalUnits: List<ValueUnit<DataSize>> = listOf(
    ValueUnit("KB", { it.decimalKilobytes }, { DataSize((it * 1000.0).toLong()) }),
    ValueUnit("MB", { it.decimalMegabytes }, { DataSize((it * 1_000_000.0).toLong()) }),
    ValueUnit("GB", { it.decimalGigabytes }, { DataSize((it * 1_000_000_000.0).toLong()) }),
    ValueUnit("TB", { it.decimalTerabytes }, { DataSize((it * 1_000_000_000_000.0).toLong()) }),
)
public object DataSizeRenderer : NumericValueClassRenderer<DataSize>(
    name = "Data Size",
    units = dataSizeBinaryUnits + dataSizeDecimalUnits,
    preferredUnits = dataSizeBinaryUnits,
)

// For each type, two lists:
//   * `*Units` — order shown in the dropdown (typically smallest to largest,
//     metric before imperial).
//   * `*Preferred` — the subset the picker scans, and ORDER MATTERS for the
//     zero / unloaded fallback: the picker returns `preferredUnits.first()`
//     when every magnitude is < 1, so the SI base unit goes first. For non-
//     zero values the order beyond the first element doesn't affect picking.

private val lengthMetricUnits: List<ValueUnit<Length>> = listOf(
    ValueUnit("nm", { it.nanometer }, { Length(it * 1e-9) }),
    ValueUnit("μm", { it.micrometer }, { Length(it * 1e-6) }),
    ValueUnit("mm", { it.millimeter }, { Length(it * 1e-3) }),
    ValueUnit("cm", { it.centimeters }, { Length(it * 1e-2) }),
    ValueUnit("m", { it.meters }, { Length(it) }),
    ValueUnit("km", { it.kilometers }, { Length(it * 1e3) }),
)
private val lengthImperialUnits: List<ValueUnit<Length>> = listOf(
    ValueUnit("in", { it.inches }, { Length(it * 0.0254) }),
    ValueUnit("ft", { it.feet }, { Length(it * 0.3048) }),
    ValueUnit("yd", { it.yards }, { Length(it * 0.9144) }),
    ValueUnit("mi", { it.miles }, { Length(it * 1609.344) }),
)
private val lengthMetricPreferred: List<ValueUnit<Length>> = listOf(
    ValueUnit("m", { it.meters }, { Length(it) }),
    ValueUnit("mm", { it.millimeter }, { Length(it * 1e-3) }),
    ValueUnit("cm", { it.centimeters }, { Length(it * 1e-2) }),
    ValueUnit("μm", { it.micrometer }, { Length(it * 1e-6) }),
    ValueUnit("nm", { it.nanometer }, { Length(it * 1e-9) }),
    ValueUnit("km", { it.kilometers }, { Length(it * 1e3) }),
)
public object LengthRenderer : NumericValueClassRenderer<Length>(
    name = "Length",
    units = lengthMetricUnits + lengthImperialUnits,
    preferredUnits = lengthMetricPreferred,
)

private val areaMetricUnits: List<ValueUnit<Area>> = listOf(
    ValueUnit("mm²", { it.squareMillimeters }, { Area(it * 1e-6) }),
    ValueUnit("cm²", { it.squareCentimeters }, { Area(it * 1e-4) }),
    ValueUnit("m²", { it.squareMeters }, { Area(it) }),
    ValueUnit("ha", { it.hectare }, { Area(it * 10_000.0) }),
    ValueUnit("km²", { it.squareKilometers }, { Area(it * 1e6) }),
)
private val areaMetricPreferred: List<ValueUnit<Area>> = listOf(
    ValueUnit("m²", { it.squareMeters }, { Area(it) }),
    ValueUnit("cm²", { it.squareCentimeters }, { Area(it * 1e-4) }),
    ValueUnit("mm²", { it.squareMillimeters }, { Area(it * 1e-6) }),
    ValueUnit("ha", { it.hectare }, { Area(it * 10_000.0) }),
    ValueUnit("km²", { it.squareKilometers }, { Area(it * 1e6) }),
)
public object AreaRenderer : NumericValueClassRenderer<Area>(
    name = "Area",
    units = areaMetricUnits + listOf(
        ValueUnit("acres", { it.acres }, { Area(it * 4046.8564224) }),
    ),
    preferredUnits = areaMetricPreferred,
)

private val volumeMetricUnits: List<ValueUnit<Volume>> = listOf(
    ValueUnit("mL", { it.milliliters }, { Volume(it * 1e-6) }),
    ValueUnit("L", { it.liters }, { Volume(it * 1e-3) }),
    ValueUnit("m³", { it.cubicMeters }, { Volume(it) }),
)
private val volumeMetricPreferred: List<ValueUnit<Volume>> = listOf(
    ValueUnit("m³", { it.cubicMeters }, { Volume(it) }),
    ValueUnit("L", { it.liters }, { Volume(it * 1e-3) }),
    ValueUnit("mL", { it.milliliters }, { Volume(it * 1e-6) }),
)
public object VolumeRenderer : NumericValueClassRenderer<Volume>(
    name = "Volume",
    units = volumeMetricUnits + listOf(
        ValueUnit("fl oz", { it.liquidOunces }, { Volume(it * 2.957352956250001E-5) }),
        ValueUnit("cup", { it.cups }, { Volume(it * 2.365882365000001E-4) }),
        ValueUnit("pt", { it.pints }, { Volume(it * 4.731764730000002E-4) }),
        ValueUnit("qt", { it.quarts }, { Volume(it * 9.463529460000004E-4) }),
        ValueUnit("gal", { it.gallons }, { Volume(it * 0.0037854117840000014) }),
        ValueUnit("ft³", { it.cubicFeet }, { Volume(it * 0.028316846592000004) }),
    ),
    preferredUnits = volumeMetricPreferred,
)

private val massMetricUnits: List<ValueUnit<Mass>> = listOf(
    ValueUnit("mg", { it.milligrams }, { Mass(it * 1e-6) }),
    ValueUnit("g", { it.grams }, { Mass(it * 1e-3) }),
    ValueUnit("kg", { it.kilograms }, { Mass(it) }),
    ValueUnit("t", { it.tonnes }, { Mass(it * 1000.0) }),
)
private val massMetricPreferred: List<ValueUnit<Mass>> = listOf(
    ValueUnit("kg", { it.kilograms }, { Mass(it) }),
    ValueUnit("g", { it.grams }, { Mass(it * 1e-3) }),
    ValueUnit("mg", { it.milligrams }, { Mass(it * 1e-6) }),
    ValueUnit("t", { it.tonnes }, { Mass(it * 1000.0) }),
)
public object MassRenderer : NumericValueClassRenderer<Mass>(
    name = "Mass",
    units = massMetricUnits + listOf(
        ValueUnit("oz", { it.weightOunces }, { Mass(it * 0.028349523125) }),
        ValueUnit("lb", { it.pounds }, { Mass(it * 0.45359237) }),
        ValueUnit("ton", { it.tons }, { Mass(it * 907.18474) }),
    ),
    preferredUnits = massMetricPreferred,
)

private val speedMetricUnits: List<ValueUnit<Speed>> = listOf(
    ValueUnit("m/s", { it.metersPerSecond }, { Speed(it) }),
    ValueUnit("km/h", { it.kilometersPerHour }, { Speed(it * 0.2777777777777778) }),
)
public object SpeedRenderer : NumericValueClassRenderer<Speed>(
    name = "Speed",
    units = speedMetricUnits + listOf(
        ValueUnit("ft/s", { it.feetPerSecond }, { Speed(it * 0.3048) }),
        ValueUnit("mph", { it.milesPerHour }, { Speed(it * 0.44704) }),
    ),
    preferredUnits = speedMetricUnits,
)

private val accelerationMetricUnits: List<ValueUnit<Acceleration>> = listOf(
    ValueUnit("m/s²", { it.metersPerSecondPerSecond }, { Acceleration(it) }),
    ValueUnit("km/h/s", { it.kilometersPerHourPerSecond }, { Acceleration(it * 0.2777777777777778) }),
)
public object AccelerationRenderer : NumericValueClassRenderer<Acceleration>(
    name = "Acceleration",
    units = accelerationMetricUnits + listOf(
        ValueUnit("ft/s²", { it.feetPerSecondPerSecond }, { Acceleration(it * 0.3048) }),
        ValueUnit("mph/s", { it.milesPerHourPerSecond }, { Acceleration(it * 0.44704) }),
    ),
    preferredUnits = accelerationMetricUnits,
)

public object ForceRenderer : NumericValueClassRenderer<Force>(
    name = "Force",
    units = listOf(
        ValueUnit("N", { it.newtons }, { Force(it) }),
        ValueUnit("lbf", { it.poundForce }, { Force(it * 4.448222) }),
    ),
    preferredUnits = listOf(
        ValueUnit("N", { it.newtons }, { Force(it) }),
    ),
)

private val pressureMetricUnits: List<ValueUnit<Pressure>> = listOf(
    ValueUnit("Pa", { it.pascals }, { Pressure(it) }),
    ValueUnit("mbar", { it.millibars }, { Pressure(it * 100.0) }),
    ValueUnit("kPa", { it.pascals / 1000.0 }, { Pressure(it * 1000.0) }),
    ValueUnit("bar", { it.bars }, { Pressure(it * 100_000.0) }),
)
public object PressureRenderer : NumericValueClassRenderer<Pressure>(
    name = "Pressure",
    units = pressureMetricUnits + listOf(
        ValueUnit("atm", { it.atmospheres }, { Pressure(it * 101325.0) }),
        ValueUnit("psi", { it.psi }, { Pressure(it * 6894.757889515778) }),
    ),
    preferredUnits = pressureMetricUnits,
)

private val energyMetricUnits: List<ValueUnit<Energy>> = listOf(
    ValueUnit("J", { it.joules }, { Energy(it) }),
    ValueUnit("kJ", { it.joules / 1000.0 }, { Energy(it * 1000.0) }),
    ValueUnit("MJ", { it.joules / 1_000_000.0 }, { Energy(it * 1_000_000.0) }),
)
// NOTE: Energy.kcal and Energy.btus in service-abstractions/data-shared are
// physically inverted (their getters return joules × the J↔unit factor instead
// of joules ÷ it, and the companion constructors mirror the same inversion).
// Going via those properties causes runaway feedback in the form's reactive
// loop and produces values that are nowhere near real kcal/BTU counts. Compute
// directly from joules to dodge that bug.
private const val JOULES_PER_KCAL: Double = 4184.0           // 1 kcal = 4184 J
private const val JOULES_PER_BTU: Double = 1055.05585262     // 1 BTU = 1055.06 J
public object EnergyRenderer : NumericValueClassRenderer<Energy>(
    name = "Energy",
    units = energyMetricUnits + listOf(
        ValueUnit("kcal", { it.joules / JOULES_PER_KCAL }, { Energy(it * JOULES_PER_KCAL) }),
        ValueUnit("BTU", { it.joules / JOULES_PER_BTU }, { Energy(it * JOULES_PER_BTU) }),
    ),
    preferredUnits = energyMetricUnits,
)

public object PowerRenderer : NumericValueClassRenderer<Power>(
    name = "Power",
    units = listOf(
        ValueUnit("W", { it.watts }, { Power(it) }),
        ValueUnit("kW", { it.kilowatts }, { Power(it * 1000.0) }),
        ValueUnit("MW", { it.watts / 1_000_000.0 }, { Power(it * 1_000_000.0) }),
    ),
)

// Temperature: magnitude-based picking is misleading (273.15 K vs 0 °C is the
// same temperature), so always default to °C.
public object TemperatureRenderer : NumericValueClassRenderer<Temperature>(
    name = "Temperature",
    units = listOf(
        ValueUnit("°C", { it.celsius }, { Temperature(it) }),
        ValueUnit("°F", { it.fahrenheit }, { Temperature((it - 32.0) * 5.0 / 9.0) }),
        ValueUnit("K", { it.kelvin }, { Temperature(it - 273.15) }),
    ),
    preferredUnits = listOf(
        ValueUnit("°C", { it.celsius }, { Temperature(it) }),
    ),
)

public object RelativeTemperatureRenderer : NumericValueClassRenderer<RelativeTemperature>(
    name = "Temperature Difference",
    units = listOf(
        ValueUnit("Δ°C", { it.celsius }, { RelativeTemperature(it) }),
        ValueUnit("Δ°F", { it.fahrenheit }, { RelativeTemperature(it * 5.0 / 9.0) }),
        ValueUnit("ΔK", { it.kelvin }, { RelativeTemperature(it) }),
    ),
    preferredUnits = listOf(
        ValueUnit("Δ°C", { it.celsius }, { RelativeTemperature(it) }),
    ),
)

public object CentsRenderer : Renderer<Cents> {
    override val name: String = "Dollars and Cents"
    override fun form(context: RenderContext<Cents>, value: MutableReactive<Cents>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        unpadded.row {
            centered.text("$")
            numberInput {
                content bind value.lens(
                    get = { it.toDouble() },
                    set = { it?.dollars ?: 0.cents }
                )
            }
        }
    }

    override fun view(context: RenderContext<Cents>, value: Reactive<Cents>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        text {
            align = Align.End
            ::content { value().toString() }
        }
    }

    override fun cellView(context: RenderContext<Cents>, value: Reactive<Cents>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        text {
            align = Align.End
            ::content { value().toString() }
            wraps = false
            ellipsis = true
        }
    }

    override fun columnWidth(context: RenderContext<Cents>, module: FormModule): Double = 8.0
}

// ===== Registration =====

public fun FormModule.registerSpecialTypes() {
    register(Selector(type = "kotlin.uuid.Uuid"), UuidRenderer)
    register(Selector(type = "com.lightningkite.uuid.Uuid"), UuidRenderer)
    register(Selector(type = "com.lightningkite.Uuid"), UuidRenderer)

    // Physical-quantity value classes — register both legacy (com.lightningkite)
    // and new (com.lightningkite.services.data) fully-qualified names.
    for (pkg in listOf("com.lightningkite", "com.lightningkite.services.data")) {
        register(Selector(type = "$pkg.EmailAddress"), EmailAddressRenderer)
        register(Selector(type = "$pkg.PhoneNumber"), PhoneNumberRenderer)
        register(Selector(type = "$pkg.GeoCoordinate"), GeoCoordinateRenderer)
        register(Selector(type = "$pkg.DataSize"), DataSizeRenderer)
        register(Selector(type = "$pkg.Length"), LengthRenderer)
        register(Selector(type = "$pkg.Area"), AreaRenderer)
        register(Selector(type = "$pkg.Volume"), VolumeRenderer)
        register(Selector(type = "$pkg.Mass"), MassRenderer)
        register(Selector(type = "$pkg.Speed"), SpeedRenderer)
        register(Selector(type = "$pkg.Acceleration"), AccelerationRenderer)
        register(Selector(type = "$pkg.Force"), ForceRenderer)
        register(Selector(type = "$pkg.Pressure"), PressureRenderer)
        register(Selector(type = "$pkg.Energy"), EnergyRenderer)
        register(Selector(type = "$pkg.Power"), PowerRenderer)
        register(Selector(type = "$pkg.Temperature"), TemperatureRenderer)
        register(Selector(type = "$pkg.RelativeTemperature"), RelativeTemperatureRenderer)
        register(Selector(type = "$pkg.Cents"), CentsRenderer)
    }
}
