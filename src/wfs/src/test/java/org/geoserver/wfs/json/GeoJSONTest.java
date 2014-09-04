/*
 * Copyright (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */

package org.geoserver.wfs.json;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;

import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;

import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.ProjectionPolicy;
import org.geoserver.config.GeoServer;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.data.util.IOUtils;
import org.geoserver.wfs.WFSInfo;
import org.geoserver.wfs.WFSTestSupport;
import org.geotools.referencing.CRS;
import org.hamcrest.Description;
import org.hamcrest.Matchers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.mockrunner.mock.web.MockHttpServletResponse;

/**
 * 
 * @author carlo cancellieri - GeoSolutions
 *
 */
public class GeoJSONTest extends WFSTestSupport {
       
    public static QName LINE3D = new QName(SystemTestData.CITE_URI, "Line3D", SystemTestData.CITE_PREFIX);
    public static QName POINT_LATLON = new QName(SystemTestData.CITE_URI, "PointLatLon", SystemTestData.CITE_PREFIX);
    public static QName POINT_LONLAT = new QName(SystemTestData.CITE_URI, "PointLonLat", SystemTestData.CITE_PREFIX);
    
    @Override
    @SuppressWarnings("unchecked")
    protected void setUpInternal(SystemTestData data) throws Exception {
        super.setUpInternal(data);
        File security = new File(getTestData().getDataDirectoryRoot(), "security");
        security.mkdir();
        File layers = new File(security, "layers.properties");
        IOUtils.copy(GeoJSONTest.class.getResourceAsStream("layers_ro.properties"), layers);
        data.addVectorLayer (LINE3D, Collections.EMPTY_MAP, getClass(), getCatalog());

        // A feature type with Lat-Lon/North-East axis ordering.
        data.addVectorLayer (POINT_LATLON, Collections.EMPTY_MAP, getClass(), getCatalog());
        CoordinateReferenceSystem crsLatLon = CRS.decode("urn:ogc:def:crs:EPSG::4326");
        FeatureTypeInfo pointLatLon = getCatalog().getFeatureTypeByName(POINT_LATLON.getPrefix(), POINT_LATLON.getLocalPart());
        pointLatLon.setNativeCRS(crsLatLon);
        pointLatLon.setSRS("urn:ogc:def:crs:EPSG::4326");
        pointLatLon.setProjectionPolicy(ProjectionPolicy.FORCE_DECLARED);
        getCatalog().save(pointLatLon);
        
        // A feature type with Lon-Lat/East-North axis ordering.
        data.addVectorLayer (POINT_LONLAT, Collections.EMPTY_MAP, getClass(), getCatalog());
        CoordinateReferenceSystem crsLonLat = CRS.decode("EPSG:4326", true);
        FeatureTypeInfo pointLonLat = getCatalog().getFeatureTypeByName(POINT_LONLAT.getPrefix(), POINT_LONLAT.getLocalPart());
        pointLatLon.setNativeCRS(crsLonLat);
        pointLatLon.setSRS("EPSG:4326");
        pointLatLon.setProjectionPolicy(ProjectionPolicy.FORCE_DECLARED);
        getCatalog().save(pointLonLat);

    }
    
    static String propValue;
    @BeforeClass
    static public void setUp() {
        propValue = System.getProperty("GEOSERVER_GEOJSON_LEGACY_CRS");
        System.setProperty("GEOSERVER_GEOJSON_LEGACY_CRS", "false");
    }
    
    @AfterClass
    static public void tearDown() {
        if(propValue==null) {
            System.clearProperty("GEOSERVER_GEOJSON_LEGACY_CRS");
        } else {
            System.setProperty("GEOSERVER_GEOJSON_LEGACY_CRS", propValue);
        }
    }
	
    @Test
    public void testFeatureBoundingDisabledCollection() throws Exception {
    	/* In GML we have the option not to compute the bounds in the response, 
    	 * and by default we don't, but GeoServer can be configured to return 
    	 * the bounds, in that case it will issue a bounds query against the store, 
    	 * which might take a long time (that depends a lot on what the store can do, 
    	 * some can compute it quickly, no idea what SDE).
    	 * For GeoJSON it seems that the "feature bounding" flag is respected 
    	 * for the single feature bounds, but not for the collection.
    	 * Looking at the spec ( http://geojson.org/geojson-spec.html ) it seems to 
    	 * me the collection bbox is not required:
    	 * "To include information on the coordinate range for geometries, features, 
    	 * or feature collections, a GeoJSON object may have a member named "bbox""
    	 * disable Feature bounding */
        
    	GeoServer gs = getGeoServer();
        
        WFSInfo wfs = getWFS();
        boolean before = wfs.isFeatureBounding();
        wfs.setFeatureBounding(false);
        try {
            gs.save( wfs );
             
        	String out = getAsString("wfs?request=GetFeature&version=1.0.0&typename=sf:AggregateGeoFeature&maxfeatures=3&outputformat="+JSONType.json);
        	JSONObject rootObject = JSONObject.fromObject( out );
         	
        	JSONObject bbox = rootObject.getJSONObject("bbox");
        	assertEquals(JSONNull.getInstance(), bbox);
        } finally {
        	wfs.setFeatureBounding(before);
            gs.save( wfs );
        }
    	
    }
    
    @Test
    public void testGet() throws Exception {	
        MockHttpServletResponse response = getAsServletResponse("wfs?request=GetFeature&version=1.0.0&typename=sf:PrimitiveGeoFeature&maxfeatures=1&outputformat="+JSONType.json);
        assertEquals("application/json", response.getContentType());
        String out = response.getOutputStreamContent();

    	
    	JSONObject rootObject = JSONObject.fromObject( out );
    	assertEquals(rootObject.get("type"),"FeatureCollection");
    	JSONArray featureCol = rootObject.getJSONArray("features");
    	JSONObject aFeature = featureCol.getJSONObject(0);
    	assertEquals(aFeature.getString("geometry_name"),"surfaceProperty");
    }
    
    @Test
    public void testGetSimpleJson() throws Exception {    
        MockHttpServletResponse response = getAsServletResponse("wfs?request=GetFeature&version=1.0.0&typename=sf:PrimitiveGeoFeature&maxfeatures=1&outputformat="+JSONType.simple_json);
        assertEquals("application/json", response.getContentType());
        String out = response.getOutputStreamContent();
        
        JSONObject rootObject = JSONObject.fromObject( out );
        assertEquals(rootObject.get("type"),"FeatureCollection");
        JSONArray featureCol = rootObject.getJSONArray("features");
        JSONObject aFeature = featureCol.getJSONObject(0);
        assertEquals(aFeature.getString("geometry_name"),"surfaceProperty");
    }

    @Test
    public void testGetJsonIdPolicyTrue() throws Exception {    
        MockHttpServletResponse response = getAsServletResponse("wfs?request=GetFeature&version=1.0.0&typename=sf:PrimitiveGeoFeature&maxfeatures=1&outputformat="+JSONType.simple_json+"&format_options=" + JSONType.ID_POLICY+":true");
        assertEquals("application/json", response.getContentType());
        String out = response.getOutputStreamContent();
        
        JSONObject rootObject = JSONObject.fromObject( out );
        assertEquals(rootObject.get("type"),"FeatureCollection");
        JSONArray featureCol = rootObject.getJSONArray("features");
        JSONObject aFeature = featureCol.getJSONObject(0);
        
        assertTrue("id", aFeature.containsKey("id"));
        Object id = aFeature.get("id");
        assertNotNull("id", id);
        assertEquals("PrimitiveGeoFeature.f001",id);
    }
    @Test
    public void testGetJsonIdPolicyFalse() throws Exception {    
        MockHttpServletResponse response = getAsServletResponse("wfs?request=GetFeature&version=1.0.0&typename=sf:PrimitiveGeoFeature&maxfeatures=1&outputformat="+JSONType.simple_json+"&format_options=" + JSONType.ID_POLICY+":false");
        assertEquals("application/json", response.getContentType());
        String out = response.getOutputStreamContent();
        
        JSONObject rootObject = JSONObject.fromObject( out );
        assertEquals(rootObject.get("type"),"FeatureCollection");
        JSONArray featureCol = rootObject.getJSONArray("features");
        JSONObject aFeature = featureCol.getJSONObject(0);

        assertFalse("supress id", aFeature.containsKey("id"));
    }

    @Test
    public void testGetJsonIdPolicyAttribute() throws Exception {    
        MockHttpServletResponse response = getAsServletResponse("wfs?request=GetFeature&version=1.0.0&typename=sf:PrimitiveGeoFeature&maxfeatures=1&outputformat="+JSONType.simple_json+"&format_options=" + JSONType.ID_POLICY+":name");
        assertEquals("application/json", response.getContentType());
        String out = response.getOutputStreamContent();
        
        JSONObject rootObject = JSONObject.fromObject( out );
        assertEquals(rootObject.get("type"),"FeatureCollection");
        JSONArray featureCol = rootObject.getJSONArray("features");
        JSONObject aFeature = featureCol.getJSONObject(0);
        
        assertTrue("id", aFeature.containsKey("id"));
        Object id = aFeature.get("id");
        assertNotNull("id", id);
        assertEquals("name-f001", id);
        JSONObject properties = aFeature.getJSONObject("properties");
        assertFalse( properties.containsKey("name"));
    }
    
    @Test
    public void testPost() throws Exception {
        String xml = "<wfs:GetFeature " + "service=\"WFS\" " + "outputFormat=\""+JSONType.json+"\" "
                + "version=\"1.0.0\" "
                + "xmlns:cdf=\"http://www.opengis.net/cite/data\" "
                + "xmlns:ogc=\"http://www.opengis.net/ogc\" "
                + "xmlns:wfs=\"http://www.opengis.net/wfs\" " + "> "
                + "<wfs:Query typeName=\"sf:PrimitiveGeoFeature\"> "
                + "</wfs:Query> " + "</wfs:GetFeature>";

        String out = postAsServletResponse( "wfs", xml ).getOutputStreamContent();
    	
    	JSONObject rootObject = JSONObject.fromObject( out );
    	assertEquals(rootObject.get("type"),"FeatureCollection");
    	JSONArray featureCol = rootObject.getJSONArray("features");
    	JSONObject aFeature = featureCol.getJSONObject(0);
    	assertEquals(aFeature.getString("geometry_name"),"surfaceProperty");
    }

    @Test
    public void testGeometryCollection() throws Exception {
    	String out = getAsString("wfs?request=GetFeature&version=1.0.0&typename=sf:AggregateGeoFeature&maxfeatures=3&outputformat="+JSONType.json);
    	
    	JSONObject rootObject = JSONObject.fromObject( out );
    	assertEquals(rootObject.get("type"),"FeatureCollection");
    	JSONArray featureCol = rootObject.getJSONArray("features");
    	JSONObject aFeature = featureCol.getJSONObject(1);
    	JSONObject aGeometry = aFeature.getJSONObject("geometry");
    	assertEquals(aGeometry.getString("type"),"MultiLineString");
    	JSONArray geomArray = aGeometry.getJSONArray("coordinates");
    	geomArray = geomArray.getJSONArray(0);
    	geomArray = geomArray.getJSONArray(0);
    	assertEquals(geomArray.getString(0), "55.174");
    	CoordinateReferenceSystem expectedCrs = getCatalog().getLayerByName(getLayerId(SystemTestData.AGGREGATEGEOFEATURE)).getResource().getCRS();
    	JSONObject aCRS = rootObject.getJSONObject("crs");
    	assertThat(aCRS.getString("type"), equalTo("name"));
    	assertThat(aCRS, encodesCRS(expectedCrs));
    }
    
    @Test
    public void testMixedCollection() throws Exception {
        String xml = "<wfs:GetFeature " + "service=\"WFS\" " + "outputFormat=\""+JSONType.json+"\" "
        + "version=\"1.0.0\" "
        + "xmlns:cdf=\"http://www.opengis.net/cite/data\" "
        + "xmlns:ogc=\"http://www.opengis.net/ogc\" "
        + "xmlns:wfs=\"http://www.opengis.net/wfs\" " + "> "
        + "<wfs:Query typeName=\"sf:PrimitiveGeoFeature\" /> "
        + "<wfs:Query typeName=\"sf:AggregateGeoFeature\" /> "
        + "</wfs:GetFeature>";
        //System.out.println("\n" + xml + "\n");
        
        String out  = postAsServletResponse( "wfs", xml).getOutputStreamContent();

        JSONObject rootObject = JSONObject.fromObject( out );
        //System.out.println(rootObject.get("type"));
        assertEquals(rootObject.get("type"),"FeatureCollection");
        
        JSONArray featureCol = rootObject.getJSONArray("features");
        
        // Check that there are at least two different types of features in here
        JSONObject aFeature = featureCol.getJSONObject(1);
        //System.out.println(aFeature.getString("id").substring(0,19));
        assertTrue(aFeature.getString("id").substring(0,19).equalsIgnoreCase("PrimitiveGeoFeature"));          
        aFeature = featureCol.getJSONObject(6);
        //System.out.println(aFeature.getString("id").substring(0,19));
        assertTrue(aFeature.getString("id").substring(0,19).equalsIgnoreCase("AggregateGeoFeature"));
               
        // Check that a feature has the expected attributes
        JSONObject aGeometry = aFeature.getJSONObject("geometry");
        //System.out.println(aGeometry.getString("type"));
        assertEquals(aGeometry.getString("type"),"MultiLineString");
    }

    @Test
    public void testCallbackFunction() throws Exception {
        JSONType.setJsonpEnabled(true);
        MockHttpServletResponse resp = getAsServletResponse("wfs?request=GetFeature&version=1.0.0&typename=sf:PrimitiveGeoFeature&maxfeatures=1&outputformat="
                + JSONType.jsonp + "&format_options=" + JSONType.CALLBACK_FUNCTION_KEY + ":myFunc");
        JSONType.setJsonpEnabled(false);
        String out = resp.getOutputStreamContent();

        assertEquals(JSONType.jsonp, resp.getContentType());
        assertTrue(out.startsWith("myFunc("));
        assertTrue(out.endsWith(")"));

        // extract the json and check it
        out = out.substring(7, out.length() - 1);
        JSONObject rootObject = JSONObject.fromObject(out);
        assertEquals(rootObject.get("type"), "FeatureCollection");
        JSONArray featureCol = rootObject.getJSONArray("features");
        JSONObject aFeature = featureCol.getJSONObject(0);
        assertEquals(aFeature.getString("geometry_name"), "surfaceProperty");
    }
    
    @Test
    public void testGetFeatureCount() throws Exception {        
        //request without filter
        String out = getAsString("wfs?request=GetFeature&version=1.0.0&typename=sf:PrimitiveGeoFeature&maxfeatures=10&outputformat="+JSONType.json);
        JSONObject rootObject = JSONObject.fromObject( out );
        assertEquals(rootObject.get("totalFeatures"),5);

        //request with filter (featureid=PrimitiveGeoFeature.f001)
        String out2 = getAsString("wfs?request=GetFeature&version=1.0.0&typename=sf:PrimitiveGeoFeature&maxfeatures=10&outputformat="+JSONType.json+"&featureid=PrimitiveGeoFeature.f001");
        JSONObject rootObject2 = JSONObject.fromObject( out2 );
        assertEquals(rootObject2.get("totalFeatures"),1);
        
        //check if maxFeatures doesn't affect totalFeatureCount; set Filter and maxFeatures
        String out3 = getAsString("wfs?request=GetFeature&version=1.0.0&typename=sf:PrimitiveGeoFeature&maxfeatures=1&outputformat="+JSONType.json+"&featureid=PrimitiveGeoFeature.f001,PrimitiveGeoFeature.f002");
        JSONObject rootObject3 = JSONObject.fromObject( out3 );
        assertEquals(rootObject3.get("totalFeatures"),2);
        
        //request with multiple featureTypes and Filter
        String out4 = getAsString("wfs?request=GetFeature&version=1.0.0&typename=sf:PrimitiveGeoFeature,sf:AggregateGeoFeature&outputformat="+JSONType.json + "&featureid=PrimitiveGeoFeature.f001,PrimitiveGeoFeature.f002,AggregateGeoFeature.f009");
        JSONObject rootObject4 = JSONObject.fromObject( out4 );
        assertEquals(rootObject4.get("totalFeatures"),3);
        
    }

    @Test
    public void testGetFeatureCountWfs20() throws Exception {        
        //request without filter
        String out = getAsString("wfs?request=GetFeature&version=2.0.0&typename=sf:PrimitiveGeoFeature&maxfeatures=10&outputformat="+JSONType.json);
        JSONObject rootObject = JSONObject.fromObject( out );
        assertEquals(rootObject.get("totalFeatures"),5);

        //request with filter (featureid=PrimitiveGeoFeature.f001)
        String out2 = getAsString("wfs?request=GetFeature&version=2.0.0&typename=sf:PrimitiveGeoFeature&maxfeatures=10&outputformat="+JSONType.json+"&featureid=PrimitiveGeoFeature.f001");
        JSONObject rootObject2 = JSONObject.fromObject( out2 );
        assertEquals(rootObject2.get("totalFeatures"),1);
        
        //check if maxFeatures doesn't affect totalFeatureCount; set Filter and maxFeatures
        String out3 = getAsString("wfs?request=GetFeature&version=2.0.0&typename=sf:PrimitiveGeoFeature&maxfeatures=1&outputformat="+JSONType.json+"&featureid=PrimitiveGeoFeature.f001,PrimitiveGeoFeature.f002");
        JSONObject rootObject3 = JSONObject.fromObject( out3 );
        assertEquals(rootObject3.get("totalFeatures"),2);
        
        //request with multiple featureTypes and Filter
        String out4 = getAsString("wfs?request=GetFeature&version=2.0.0&typename=sf:PrimitiveGeoFeature,sf:AggregateGeoFeature&outputformat="+JSONType.json + "&featureid=PrimitiveGeoFeature.f001,PrimitiveGeoFeature.f002,AggregateGeoFeature.f009");
        JSONObject rootObject4 = JSONObject.fromObject( out4 );
        assertEquals(rootObject4.get("totalFeatures"),3);
        
    }
 
    @Test
    public void testGetFeatureLine3D() throws Exception {
        JSONObject collection = (JSONObject) getAsJSON("wfs?request=GetFeature&version=1.0.0&typename=" + getLayerId(LINE3D)
                + "&outputformat=" + JSONType.json);
        // print(collection);
        assertEquals(1, collection.getInt("totalFeatures"));
        //assertEquals("4327", collection.getJSONObject("crs").getJSONObject("properties").getString("code"));
        JSONArray features = collection.getJSONArray("features");
        assertEquals(1, features.size());
        JSONObject feature = features.getJSONObject(0);
        JSONObject geometry = feature.getJSONObject("geometry");
        assertEquals("LineString", geometry.getString("type"));
        JSONArray coords = geometry.getJSONArray("coordinates");
        JSONArray c1 = coords.getJSONArray(0);
        assertEquals(0, c1.getInt(0));
        assertEquals(0, c1.getInt(1));
        assertEquals(50, c1.getInt(2));
        JSONArray c2 = coords.getJSONArray(1);
        assertEquals(120, c2.getInt(0));
        assertEquals(0, c2.getInt(1));
        assertEquals(100, c2.getInt(2));
        
        CoordinateReferenceSystem expectedCrs = CRS.decode("EPSG:4327");
        JSONObject aCRS = collection.getJSONObject("crs");
        assertThat(aCRS, encodesCRS(expectedCrs));
    }
    
    // Checks that the result is in EAST_NORTH/LON_LAT order regardless of the source order
    protected void doAxisSwapTest(QName layer, CRS.AxisOrder sourceOrder) throws Exception {
        // Failure here means the setup for the test is broken and would invalidate the test
        assertThat(CRS.getAxisOrder(
                getCatalog().getFeatureTypeByName(layer.getPrefix(), layer.getLocalPart()).getCRS()
                ), is(sourceOrder));

        JSONObject collection = (JSONObject) getAsJSON("wfs?request=GetFeature&version=1.0.0&typename=" + getLayerId(layer)
                + "&outputformat=" + JSONType.json);
        // print(collection);
        assertThat(collection.getInt("totalFeatures"), is(3));
        //assertEquals("4327", collection.getJSONObject("crs").getJSONObject("properties").getString("code"));
        JSONArray features = collection.getJSONArray("features");
        assertThat((Collection<?>)features, Matchers.hasSize(3));
        JSONObject feature = features.getJSONObject(0);
        
        JSONObject geometry = feature.getJSONObject("geometry");
        assertThat(geometry.getString("type"), is("Point"));
        
        JSONArray coords = geometry.getJSONArray("coordinates");
        assertThat((Iterable<?>)coords, contains((Object)120, 0));
        
        JSONArray bbox = collection.getJSONArray("bbox");
        assertThat((Iterable<?>)bbox, Matchers.contains((Object)(-170), -30, 120, 45));
        
        CoordinateReferenceSystem expectedCrs = CRS.decode("EPSG:4326");
        JSONObject aCRS = collection.getJSONObject("crs");
        assertThat(aCRS, encodesCRS(expectedCrs));
    }
    
    @Test
    public void testGetFeatureAxisSwap() throws Exception {
        // Check that a NORTH_EAST source is swapped
        doAxisSwapTest(POINT_LATLON, CRS.AxisOrder.NORTH_EAST);
     }
    
    @Test
    public void testGetFeatureNoAxisSwap() throws Exception {
        // Check that an EAST_NORTH source is not swapped
        doAxisSwapTest(POINT_LONLAT, CRS.AxisOrder.EAST_NORTH);
     }
    
    @Test
    public void testGetFeatureCRS() throws Exception {
        QName layer = SystemTestData.LINES;
        JSONObject collection = (JSONObject) getAsJSON("wfs?request=GetFeature&version=1.0.0&typename=" + getLayerId(layer)
                + "&outputformat=" + JSONType.json);
        CoordinateReferenceSystem expectedCrs = getCatalog().getLayerByName(getLayerId(layer)).getResource().getCRS();
        JSONObject aCRS = collection.getJSONObject("crs");
        assertThat(aCRS, encodesCRS(expectedCrs));
    }
    
    private org.hamcrest.Matcher<JSONObject> encodesCRS(final CoordinateReferenceSystem crs) {
        return new org.hamcrest.BaseMatcher<JSONObject>(){
            
            @Override
            public boolean matches(Object item) {
                // Try to decode the CRS with both axis orders and check if either matches against
                // the expected CRS.  Sorry, this is a horrible hack. KS
                CoordinateReferenceSystem decodedDefault = decodeCRS((JSONObject)item, false);
                if(CRS.equalsIgnoreMetadata(crs, decodedDefault)) return true;
                CoordinateReferenceSystem decodedXY = decodeCRS((JSONObject)item, true);
                if(CRS.equalsIgnoreMetadata(crs, decodedXY)) return true;
                String identifier = ((JSONObject)item).getJSONObject("properties").getString("name");
                Pattern p = Pattern.compile("^urn:ogc:def:crs:EPSG:[^:]*:(\\d+)$");
                Matcher m = p.matcher(identifier);
                if(m.matches()){
                    String code = "EPSG:"+m.group(1);
                    CoordinateReferenceSystem decodedStripped;
                    try {
                        decodedStripped = CRS.decode(code, true);
                    } catch (FactoryException e) {
                        throw new IllegalStateException(e);
                    }
                    if(CRS.equalsIgnoreMetadata(crs, decodedStripped)) return true;
                }
                
                return false;
            }
            
            @Override
            public void describeTo(Description description) {
                description.appendText("JSON representation of CRS ");
                description.appendValue(crs);
            }
            
        };
    }
    
    static private CoordinateReferenceSystem decodeCRS(JSONObject json, boolean forceXY) {
        if(!json.getString("type").equals("name")) throw new IllegalArgumentException();
        String identifier = json.getJSONObject("properties").getString("name");
        try {
            return CRS.decode(identifier, forceXY);
        }catch (FactoryException e) {
            throw new IllegalStateException(e);
        }
    }
}
