package com.mapboxnavigation

import android.content.Context
import android.util.AttributeSet
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapView

open class MapviewDestroy : MapView {

    constructor(context: Context, mapInitOptions: MapInitOptions = MapInitOptions(context)): super(context, mapInitOptions){}

    constructor(context: Context, attrs: AttributeSet?): super(context, attrs){}

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): super(context, attrs, defStyleAttr){}

    override fun onDestroy() {

    }
}
