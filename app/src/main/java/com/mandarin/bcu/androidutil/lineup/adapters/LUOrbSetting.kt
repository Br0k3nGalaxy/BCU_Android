package com.mandarin.bcu.androidutil.lineup.adapters

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mandarin.bcu.R
import com.mandarin.bcu.androidutil.StaticStore
import com.mandarin.bcu.androidutil.io.ErrorLogWriter
import com.mandarin.bcu.androidutil.lineup.LineUpView
import com.mandarin.bcu.androidutil.supports.SingleClick
import com.mandarin.bcu.util.Interpret
import common.CommonStatic
import common.battle.BasisSet
import common.battle.data.Orb
import common.util.Data
import common.util.unit.Form

class LUOrbSetting : Fragment() {
    companion object {
        fun newInstance(line: LineUpView) : LUOrbSetting {
            val o = LUOrbSetting()
            o.setLineup(line)

            return o
        }
    }

    private var f: Form? = null
    private var orb = ArrayList<IntArray>()
    private lateinit var line: LineUpView

    private var isUpdating = false

    private val obj = Object()

    private val traits = intArrayOf(R.string.sch_red, R.string.sch_fl, R.string.sch_bla, R.string.sch_me, R.string.sch_an, R.string.sch_al, R.string.sch_zo)
    private val grades = arrayOf("D", "C", "B", "A", "S")

    private val traitData = ArrayList<Int>()
    private val typeData = ArrayList<Int>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.lineup_orb_setting, container, false)

        val c = context ?: return v

        val obj = Object()

        val const = v.findViewById<ConstraintLayout>(R.id.orbconst)

        const.visibility = View.INVISIBLE

        update(v)

        synchronized(obj) {
            while(isUpdating) {
                try{
                    obj.wait()
                } catch (e: InterruptedException) {
                    ErrorLogWriter.writeLog(e, StaticStore.upload, c)
                }
            }
        }

        const.visibility = View.VISIBLE

        return v
    }

    fun update() {
        val v = view ?: return
        val c = context ?: return

        val obj = Object()

        val const = v.findViewById<ConstraintLayout>(R.id.orbconst)

        const.visibility = View.INVISIBLE

        update(v)

        synchronized(obj) {
            while(isUpdating) {
                try{
                    obj.wait()
                } catch (e: InterruptedException) {
                    ErrorLogWriter.writeLog(e, StaticStore.upload, c)
                }
            }
        }

        const.visibility = View.VISIBLE


    }

    private fun listeners(v: View) {
        val orbs = v.findViewById<Spinner>(R.id.orbspinner)
        val type = v.findViewById<Spinner>(R.id.orbtype)
        val trait = v.findViewById<Spinner>(R.id.orbtrait)
        val grade = v.findViewById<Spinner>(R.id.orbgrade)
        val add = v.findViewById<FloatingActionButton>(R.id.orbadd)
        val remove = v.findViewById<FloatingActionButton>(R.id.orbremove)
        val image = v.findViewById<ImageView>(R.id.orbimage)
        val desc = v.findViewById<TextView>(R.id.orbdesc)

        f ?: return

        val o = f?.orbs ?: return

        val c = context ?: return

        val slot = o.slots != -1

        orbs.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if(position >= orb.size) {
                    return
                }

                updateSpinners(type, trait, grade, slot, orb[position])
                generateImage(orb[position], image)

                if(orb[position].isNotEmpty() && orb[position][0] == 0) {
                    val orbMap = CommonStatic.getBCAssets().ORB[orb[position][Data.ORB_TYPE]] ?: return

                    generateTraitData(orbMap)
                }
            }
        }

        add.setOnClickListener(object : SingleClick() {
            override fun onSingleClick(v: View?) {
                if(!slot) {
                    orb.add(intArrayOf(0, Data.TB_RED, 0))

                    setData()

                    val a0 = object : ArrayAdapter<String>(c, R.layout.spinnersmall, generateOrbTexts().toTypedArray()){
                        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                            val vi = super.getDropDownView(position, convertView, parent)

                            (vi as TextView).text = generateOrbTextAt(position)

                            return vi
                        }
                    }

                    orbs.adapter = a0
                    orbs.setSelection(orb.size-1)

                    if(orb.isNotEmpty()) {
                        remove.show()
                        setAppear(image, orbs, type, trait, grade)
                    } else {
                        remove.hide()
                    }

                    setLevel()
                    updateDescription(orb[orbs.selectedItemPosition], desc)
                }
            }

        })

        remove.setOnClickListener(object : SingleClick() {
            override fun onSingleClick(v: View?) {
                if(!slot && orb.isNotEmpty()) {
                    val p = orbs.selectedItemPosition

                    orb.removeAt(orbs.selectedItemPosition)

                    setData()

                    val a0 = object : ArrayAdapter<String>(c, R.layout.spinnersmall, generateOrbTexts().toTypedArray()) {
                        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                            val vi = super.getDropDownView(position, convertView, parent)

                            (vi as TextView).text = generateOrbTextAt(position)

                            return vi
                        }
                    }

                    orbs.adapter = a0

                    if(p >= orb.size) {
                        orbs.setSelection(orb.size-1)
                    } else {
                        orbs.setSelection(p)
                    }

                    setLevel()

                    if(orb.isEmpty()) {
                        remove.hide()
                        setDisappear(image, type, trait, grade, orbs)
                        updateDescription(intArrayOf(), desc)
                    } else {
                        remove.show()
                        updateDescription(orb[orbs.selectedItemPosition], desc)
                    }
                }
            }

        })

        type.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if(orbs.selectedItemPosition >= orb.size || orbs.selectedItemPosition < 0) {
                    return
                }

                var data = orb[orbs.selectedItemPosition]

                if(slot) {
                    if(position == 0) {
                        orb[orbs.selectedItemPosition] = intArrayOf()

                        setLevel()
                        updateDescription(orb[orbs.selectedItemPosition], desc)

                        setDisappear(image, trait, grade)

                        val text = orbs.getChildAt(0) ?: return

                        (text as TextView).text = generateOrbTextAt(orbs.selectedItemPosition)

                        return
                    } else {
                        if(data.isEmpty()) {
                            orb[orbs.selectedItemPosition] = intArrayOf(0, Data.TB_RED, 0)
                            data = orb[orbs.selectedItemPosition]
                        }

                        data[0] = typeData[position-1]

                        trait.isEnabled = true
                        grade.isEnabled = true

                        orb[orbs.selectedItemPosition] = data
                    }
                } else {
                    data[0] = typeData[position]
                }

                setAppear(image, trait, grade)

                val od = CommonStatic.getBCAssets().ORB[data[Data.ORB_TYPE]] ?: return

                generateTraitData(od)

                if(!od.containsKey(data[1])) {
                    data[1] = Data.TB_RED
                }

                val t = ArrayList<String>()

                for(i in od.keys) {
                    val r = Orb.reverse(i)

                    if(r >= traits.size) {
                        Log.e("LUOrbSetting", "Invalid trait data in updateSpinners() : $i")
                        return
                    } else {
                        t.add(c.getString(traits[r]))
                    }
                }

                val a1 = ArrayAdapter(c, R.layout.spinnersmall, t.toTypedArray())

                trait.adapter = a1

                trait.setSelection(traitData.indexOf(data[1]), false)

                val g = od[data[1]] ?: return

                val gr = ArrayList<String>()

                for(i in g) {
                    if(i >= grades.size) {
                        Log.e("LUOrbSetting", "Invalid grade data int updateSpinners() : $i")
                        return
                    } else {
                        gr.add(grades[i])
                    }
                }

                val a2 = ArrayAdapter(c, R.layout.spinnersmall, gr.toTypedArray())

                grade.adapter = a2

                grade.setSelection(data[2], false)

                orb[orbs.selectedItemPosition] = data

                setLevel()
                updateDescription(orb[orbs.selectedItemPosition], desc)

                generateImage(orb[orbs.selectedItemPosition], image)

                val text = orbs.getChildAt(0) ?: return

                (text as TextView).text = generateOrbTextAt(orbs.selectedItemPosition)
            }
        }

        trait.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if(orbs.selectedItemPosition >= orb.size || orbs.selectedItemPosition < 0) {
                    return
                }

                val data = orb[orbs.selectedItemPosition]

                if(data.isEmpty()) {
                    return
                }

                val od = CommonStatic.getBCAssets().ORB[data[Data.ORB_TYPE]] ?: return

                if(position >= traitData.size) {
                    return
                }

                data[1] = traitData[position]

                orb[orbs.selectedItemPosition] = data

                val g = od[data[1]] ?: return

                val gr = ArrayList<String>()

                for(i in g) {
                    if(i >= grades.size) {
                        Log.e("LUOrbSetting", "Invalid grade data int updateSpinners() : $i")
                        return
                    } else {
                        gr.add(grades[i])
                    }
                }

                val a2 = ArrayAdapter(c, R.layout.spinnersmall, gr.toTypedArray())

                grade.adapter = a2

                grade.setSelection(data[2], false)

                generateImage(orb[orbs.selectedItemPosition], image)

                val text = orbs.getChildAt(0) ?: return

                (text as TextView).text = generateOrbTextAt(orbs.selectedItemPosition)

                setLevel()
                updateDescription(orb[orbs.selectedItemPosition], desc)
            }
        }

        grade.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if(orbs.selectedItemPosition >= orb.size || orbs.selectedItemPosition < 0) {
                    return
                }

                val data = orb[orbs.selectedItemPosition]

                if(data.isEmpty())
                    return

                data[2] = position

                orb[orbs.selectedItemPosition] = data

                setLevel()
                updateDescription(orb[orbs.selectedItemPosition], desc)

                generateImage(orb[orbs.selectedItemPosition], image)

                val text = orbs.getChildAt(0) ?: return

                (text as TextView).text = generateOrbTextAt(orbs.selectedItemPosition)
            }
        }
    }

    private fun update(v: View) {
        f = if (StaticStore.position[0] == -1)
            null
        else if (StaticStore.position[0] == 100)
            line.repform
        else {
            if (StaticStore.position[0] * 5 + StaticStore.position[1] >= StaticStore.currentForms.size)
                null
            else
                StaticStore.currentForms[StaticStore.position[0] * 5 + StaticStore.position[1]]
        }

        val orbs = v.findViewById<Spinner>(R.id.orbspinner)
        val type = v.findViewById<Spinner>(R.id.orbtype)
        val trait = v.findViewById<Spinner>(R.id.orbtrait)
        val grade = v.findViewById<Spinner>(R.id.orbgrade)
        val add = v.findViewById<FloatingActionButton>(R.id.orbadd)
        val remove = v.findViewById<FloatingActionButton>(R.id.orbremove)
        val image = v.findViewById<ImageView>(R.id.orbimage)
        val desc = v.findViewById<TextView>(R.id.orbdesc)

        if(f == null || f?.orbs == null) {
            setDisappear(orbs, type, trait, grade, add, remove, image, desc)

            synchronized(obj) {
                isUpdating = false
                obj.notifyAll()
            }

            return
        } else {
            setAppear(orbs, type)
        }

        val f = this.f

        if(f == null) {
            synchronized(obj) {
                isUpdating = false
                obj.notifyAll()
            }

            return
        }

        val l = BasisSet.current().sele.lu.getLv(f)

        val o = f.orbs

        if(o == null) {
            synchronized(obj) {
                isUpdating = false
                obj.notifyAll()
            }

            return
        }

        if(l == null) {
            synchronized(obj) {
                isUpdating = false
                obj.notifyAll()
            }

            return
        }

        if(l.orbs == null && f.orbs != null) {
            if(f.orbs.slots != -1) {
                l.orbs = Array(f.orbs.slots) { intArrayOf()}
            } else {
                l.orbs = Array(0) { intArrayOf()}
            }
        }

        parseData()

        val c = context

        if(c == null) {
            synchronized(obj) {
                isUpdating = false
                obj.notifyAll()
            }

            return
        }

        val result = generateOrbTexts()

        val oa = object : ArrayAdapter<String>(c, R.layout.spinnersmall, result.toTypedArray()) {
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val vi = super.getDropDownView(position, convertView, parent)

                (vi as TextView).text = generateOrbTextAt(position)

                return vi
            }
        }

        orbs.adapter = oa

        if(orb.isNotEmpty()) {
            orbs.setSelection(0)
        } else {
            val types = ArrayList<String>()

            if(f.orbs.slots != -1) {
                types.add(c.getString(R.string.unit_info_t_none))
                types.add(c.getString(R.string.orb_atk))
                types.add(c.getString(R.string.orb_def))
            } else {
                types.add(c.getString(R.string.orb_atk))
                types.add(c.getString(R.string.orb_def))
            }

            val a0 = ArrayAdapter(c, R.layout.spinnersmall, types.toTypedArray())

            type.adapter = a0
        }

        if(o.slots == -1) {
            if(l.orbs.isEmpty()) {
                setAppear(orbs, add)
                setDisappear(type, trait, grade, image, remove)
            } else {
                setAppear(type, trait, grade, image, remove, orbs, add)
            }
        } else {
            if(l.orbs[0].isEmpty()) {
                setAppear(type, orbs)
                setDisappear(grade, trait, add, remove, image)
            } else {
                setDisappear(add, remove)
                setAppear(type, orbs, trait, grade, image)
            }
        }

        listeners(v)

        synchronized(obj) {
            isUpdating = false
            obj.notifyAll()
        }
    }

    private fun parseData() {
        f ?: return

        orb.clear()

        val data = BasisSet.current().sele.lu.getLv(f).orbs ?: return

        for(d in data) {
            orb.add(d)
        }
    }

    private fun updateSpinners(v: Spinner, v1: Spinner, v2: Spinner, slot: Boolean, data: IntArray) {
        val c = context ?: return

        val u = f?.unit ?: return

        var str = false
        var mas = false
        var res = false

        if (slot) {
            val abi = f?.du?.abi ?: 0

            str = (abi and Data.AB_GOOD) != 0
            mas = (abi and Data.AB_MASSIVE) != 0
            res = (abi and Data.AB_RESIST) != 0
        } else {
            for(form in u.forms) {
                str = str or ((form.du.abi and Data.AB_GOOD) != 0)
                mas = mas or ((form.du.abi and Data.AB_MASSIVE) != 0)
                res = res or ((form.du.abi and Data.AB_RESIST) != 0)
            }
        }

        val types = ArrayList<String>()

        if(slot) {
            types.add(c.getString(R.string.unit_info_t_none))
        }

        typeData.clear()

        types.add(c.getString(R.string.orb_atk))
        typeData.add(Data.ORB_ATK)
        types.add(c.getString(R.string.orb_def))
        typeData.add(Data.ORB_RES)

        if(str) {
            types.add(c.getString(R.string.orb_str))
            typeData.add(Data.ORB_STRONG)
        }

        if(mas) {
            types.add(c.getString(R.string.orb_mas))
            typeData.add(Data.ORB_MASSIVE)
        }

        if(res) {
            types.add(c.getString(R.string.orb_res))
            typeData.add(Data.ORB_RESISTANT)
        }

        val a0 = ArrayAdapter(c, R.layout.spinnersmall, types.toTypedArray())

        v.adapter = a0

        if(data.isEmpty()) {
            if(slot) {
                v.setSelection(0, false)

                setDisappear(v1, v2)

                v1.isEnabled = false
                v2.isEnabled = false

                return
            } else {
                Log.e("LUOrbSetting", "Invalid format detected in updateSpinners() ! : ${data.contentToString()}")
                return
            }
        } else {
            v1.isEnabled = true
            v2.isEnabled = true

            if(slot) {
                v.setSelection(typeData.indexOf(data[0])+1, false)
            } else {
                v.setSelection(typeData.indexOf(data[0]), false)
            }
        }

        setAppear(v1, v2)

        val od = CommonStatic.getBCAssets().ORB[data[Data.ORB_TYPE]] ?: return

        val t = ArrayList<String>()

        for(i in od.keys) {
            val r = Orb.reverse(i)

            if(r >= traits.size) {
                Log.e("LUOrbSetting", "Invalid trait data in updateSpinners() : $i")
                return
            } else {
                t.add(c.getString(traits[r]))
            }
        }

        val a1 = ArrayAdapter(c, R.layout.spinnersmall, t.toTypedArray())

        v1.adapter = a1

        generateTraitData(od)

        v1.setSelection(traitData.indexOf(data[1]), false)

        val g = od[data[1]] ?: return

        val gr = ArrayList<String>()

        for(i in g) {
            if(i >= grades.size) {
                Log.e("LUOrbSetting", "Invalid grade data int updateSpinners() : $i")
                return
            } else {
                gr.add(grades[i])
            }
        }

        val a2 = ArrayAdapter(c, R.layout.spinnersmall, gr.toTypedArray())

        v2.adapter = a2

        v2.setSelection(data[2], false)
    }

    private fun generateOrbTexts() : List<String> {
        val res = ArrayList<String>()

        val c = context ?: return res

        f ?: return res

        val o = f?.orbs ?: return res

        if(o.slots != -1) {
            val l = BasisSet.current().sele.lu.getLv(f)

            if(l.orbs == null) {
                for(i in 0 until o.slots) {
                    res.add(c.getString(R.string.lineup_orb)+" ${i+1} - "+c.getString(R.string.unit_info_t_none))
                }

                return res
            }

            for(i in l.orbs.indices) {
                val data = l.orbs[i]

                if(data.isEmpty()) {
                    res.add(c.getString(R.string.lineup_orb)+" ${i+1} - "+c.getString(R.string.unit_info_t_none))
                } else {
                    res.add(c.getString(R.string.lineup_orb)+" ${i+1} - {${getType(data[0])}, ${getTrait(data[1])}, ${getGrade(data[2])}}")
                }
            }
        } else {
            val l = BasisSet.current().sele.lu.getLv(f) ?: return res

            if(l.orbs == null || l.orbs.isEmpty()) {
                return res
            } else {
                for(i in l.orbs.indices) {
                    val data = l.orbs[i]

                    if(data.isEmpty()) {
                        Log.e("LUOrbSetting","Invalid format detected in generateOrbTexts() ! : ${l.orbs?.contentDeepToString()}")
                        return res
                    } else {
                        res.add(c.getString(R.string.lineup_orb)+" ${i+1} - {${getType(data[0])}, ${getTrait(data[1])}, ${getGrade(data[2])}}")
                    }
                }
            }
        }

        return res
    }

    private fun generateOrbTextAt(index: Int) : String {
        val c = context ?: return ""

        f ?: return ""

        val o = f?.orbs ?: return ""

        if(o.slots != -1) {
            val l = BasisSet.current().sele.lu.getLv(f)

            if(l.orbs == null) {
                return c.getString(R.string.lineup_orb)+" ${index+1} - "+c.getString(R.string.unit_info_t_none)
            }

            if(index >= l.orbs.size)
                return ""

            val data = orb[index]

            return if(data.isEmpty()) {
                c.getString(R.string.lineup_orb)+" ${index+1} - "+c.getString(R.string.unit_info_t_none)
            } else {
                c.getString(R.string.lineup_orb)+" ${index+1} - {${getType(data[0])}, ${getTrait(data[1])}, ${getGrade(data[2])}}"
            }
        } else {
            val l = BasisSet.current().sele.lu.getLv(f) ?: return ""

            return if(l.orbs == null || l.orbs.isEmpty()) {
                ""
            } else {
                val data = orb[index]

                if(data.isEmpty()) {
                    Log.e("LUOrbSetting","Invalid format detected in generateOrbTexts() ! : ${l.orbs?.contentDeepToString()}")
                    ""
                } else {
                    c.getString(R.string.lineup_orb)+"${index+1} - {${getType(data[0])}, ${getTrait(data[1])}, ${getGrade(data[2])}}"
                }
            }
        }
    }

    fun setLineup(line: LineUpView) {
        this.line = line
    }

    private fun getType(type: Int) : String {
        val c = context ?: return "Unknown Type $type"

        return when(type) {
            0 -> {
                c.getString(R.string.orb_atk)
            }
            1 -> {
                c.getString(R.string.orb_def)
            }
            2 -> {
                c.getString(R.string.orb_str)
            }
            3 -> {
                c.getString(R.string.orb_mas)
            }
            4 -> {
                c.getString(R.string.orb_res)
            }
            else -> {
                return "Unknown Type $type"
            }
        }
    }

    private fun getTrait(trait: Int) : String {
        val r = StringBuilder()

        for(i in Interpret.TRAIT.indices) {
            if(((trait shr i) and 1) > 0) {
                r.append(Interpret.TRAIT[i]).append("/ ")
            }
        }

        var res = r.toString()

        if(res.endsWith("/ ")) {
            res = res.substring(0, r.length - 2)
        }

        return res
    }

    private fun getGrade(grade: Int) : String {
        return when(grade) {
            0 -> "D"
            1 -> "C"
            2 -> "B"
            3 -> "A"
            4 -> "S"
            else -> "Unknown Grade $grade"
        }
    }

    private fun setData() {
        f ?: return

        val o = BasisSet.current().sele.lu.getLv(f) ?: return

        o.orbs = orb.toTypedArray()
    }

    private fun setAppear(vararg  view: View) {
        for(v in view) {
            if(v is FloatingActionButton) {
                if(v.isOrWillBeHidden)
                    v.show()
            } else {
                if(v.visibility == View.GONE || v.visibility == View.INVISIBLE)
                    v.visibility = View.VISIBLE
            }
        }
    }

    private fun generateImage(data: IntArray?, v: ImageView) {
        val c = context ?: return

        if(data == null || data.isEmpty()) {
            v.visibility = View.INVISIBLE
            return
        } else {
            v.visibility = View.VISIBLE
        }

        val b = StaticStore.empty(c, 96f, 96f)

        val cv = Canvas(b)

        val p = Paint()

        cv.drawBitmap(StaticStore.getResizeb(CommonStatic.getBCAssets().TRAITS[Orb.reverse(data[Data.ORB_TRAIT])].bimg() as Bitmap, c, 96f), 0f, 0f, p)

        p.alpha = (255 * 0.75).toInt()

        cv.drawBitmap(StaticStore.getResizeb(CommonStatic.getBCAssets().TYPES[data[Data.ORB_TYPE]].bimg() as Bitmap, c, 96f), 0f, 0f, p)

        p.alpha = 255

        cv.drawBitmap(StaticStore.getResizeb(CommonStatic.getBCAssets().GRADES[data[Data.ORB_GRADE]].bimg() as Bitmap, c, 96f), 0f, 0f, p)

        v.setImageBitmap(b)
    }

    private fun generateTraitData(data: Map<Int, List<Int>>) {
        traitData.clear()

        for(i in data.keys) {
            traitData.add(i)
        }
    }

    private fun setDisappear(vararg view: View) {
        for(v in view) {
            when (v) {
                is FloatingActionButton -> {
                    if(v.isOrWillBeShown) {
                        v.hide()
                    }
                }
                is ImageView -> {
                    if(v.visibility == View.VISIBLE) {
                        v.visibility = View.INVISIBLE
                    }
                }
                is Spinner -> {
                    if(v.visibility == View.VISIBLE) {
                        v.visibility = View.INVISIBLE
                    }
                }
                else -> {
                    if(v.visibility == View.VISIBLE) {
                        v.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun setLevel() {
        f ?: return

        val o = Array(orb.size) { i -> orb[i]}

        val l = BasisSet.current().sele.lu.getLv(f) ?: return

        l.orbs = o

        val c = context ?: return

        StaticStore.saveLineUp(c)
    }

    private fun updateDescription(data: IntArray, text: TextView) {
        val c = context ?: return

        if(data.isEmpty()) {
            text.text = null
            return
        }

        val s = when(data[Data.ORB_TYPE]) {
            Data.ORB_ATK -> c.getString(R.string.orb_atk_desc)
                .replace("_",Data.ORB_ATK_MULTI[data[2]].toString())

            Data.ORB_RES -> c.getString(R.string.orb_def_desc)
                .replace("_",Data.ORB_RES_MULTI[data[2]].toString())

            Data.ORB_STRONG -> c.getString(R.string.orb_str_desc)
                .replace("_", Data.ORB_STR_ATK_MULTI[data[Data.ORB_GRADE]].toString())
                .replace("-", Data.ORB_STR_DEF_MULTI[data[Data.ORB_GRADE]].toString())

            Data.ORB_MASSIVE -> c.getString(R.string.orb_mas_desc)
                .replace("_", Data.ORB_MASSIVE_MULTI[data[Data.ORB_GRADE]].toString())

            else -> c.getString(R.string.orb_res_desc)
                .replace("_", Data.ORB_RESISTANT_MULTI[data[Data.ORB_GRADE]].toString())
        }


        text.visibility = View.VISIBLE
        text.text = s
    }
}