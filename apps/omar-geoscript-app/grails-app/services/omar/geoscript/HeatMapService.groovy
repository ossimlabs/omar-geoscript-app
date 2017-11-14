package omar.geoscript


import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
//import org.apache.http.ssl.TrustStrategy
//import org.apache.http.ssl.TrustSelfSignedStrategy
import org.apache.http.util.EntityUtils;

import javax.net.ssl.SSLContext
import java.security.KeyStore

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import groovy.json.JsonSlurper;
import java.io.BufferedReader
import java.io.InputStreamReader
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import com.fasterxml.jackson.core.type.TypeReference;


import org.springframework.beans.factory.annotation.Value
import geoscript.geom.*
import geoscript.feature.*
import geoscript.layer.Layer
import geoscript.layer.io.GeoJSONReader
import geoscript.workspace.*
import geoscript.process.Process as GeoScriptProcess
import geoscript.style.ColorMap
import geoscript.render.Map as GeoScriptMap


class HeatMapService {

/*    @Value('${geoscript.elasticsearch.host}')
    String host

    @Value('${geoscript.elasticsearch.keystorefile}')
    String keystorepass

    @Value('${geoscript.elasticsearch.keystorepass}')
    String keystoreloc

    @Value('${geoscript.elasticsearch.port}')
    Integer port

    @Value('${geoscript.elasticsearch.index}')
    String index

    @Value('${geoscript.elasticsearch.search}')
    String searchIndices */

    private Boolean isValidJson(String maybeJson){
        try {
            final ObjectMapper mapper = new ObjectMapper();
            mapper.readTree(maybeJson);
            return true;
        } catch (IOException e) {
            return false;
        }
    }


    def getTile(String request) {
        Layer layer
        layer = getLayer(request)
    }

    Layer getLayer(String req) 
    {
        Workspace workspace = new Memory()
        Schema schema = new Schema("heatmap", [
            new Field("geom","Point","EPSG:4326")
        ])
        Layer layer = workspace.create(schema)

        Integer count = 0;
        URL url = new URL(req);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        InputStream is = conn.getInputStream();
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);
        def result = new JsonSlurper().parse(br)
        def buffer = new ByteArrayOutputStream()
        def color1 = "#FFFFFF"
        def color2 = "#4444FF"
        def color3 = "#FF0000"
        def color4 = "#FFFF00"
        def height = "256"
        def width = "256"

        br.close();

        layer.withWriter{ writer ->
            for(Integer i = 0;i<result.hits.hits.size();i++)
            {
                if((isValidJson(result.hits.hits.getAt(i)._source.message))) {
                    Feature feature = writer.newFeature
                    Map<String, Object> logmap = new ObjectMapper().readValue(result.hits.hits.getAt(i)._source.message, HashMap.class);

                    println "logmap" + logmap

                    def minx = logmap.bbox.minX
                    def miny = logmap.bbox.minY
                    def maxx = logmap.bbox.maxX
                    def maxy = logmap.bbox.maxY
                    def srs = logmap.bbox.proj.id

                    // temporary print to make sure values are being read in fine
                    println "\nminx" + minx
                    println "\nminy" + miny
                    println "\nmaxx" + maxx
                    println "\nmaxy" + maxy
                    println "\nsrs" + srs

                    // causing crash...putting temp values for testing
                    // def width = logmap.params.width
                    // def height = logmap.params.height

                    // temporary print to make sure values are being read in fine
                    println "\nwidth" + width
                    println "\nheight" + height

                    Bounds bounds = new Bounds(minx, miny, maxx, maxy)
                    bounds.proj = srs

                    def proc = new GeoScriptProcess( "vec:Heatmap" )
                    

                    def raster = proc.execute(
                            data: logmap,
                            radiusPixels: 20,
                            pixelsPerCell: 1,
                            outputBBOX: bounds.env,
                            outputWidth: width,
                            outputHeight: height
                    )?.result

                    raster.sytle = new ColorMap( [
                            [color: color1, quantity: 0, label: "nodata", opacity: 0],
                            [color: color1, quantity: 0.02, label: "nodata", opacity: 0],
                            [color: color2, quantity: 0.1, label: "nodata"],
                            [color: color3, quantity: 0.5, label: "values"],
                            [color: color4, quantity: 1.0, label: "values"]
                    ] ).opacity( 0.25 )

                    def map = new GeoScriptMap(
                            width: width,
                            height: height,
                            type:logmap.format.split('/')[-1],
                            proj: srs,
                            bounds: bounds,
                            layers: [raster] )

                    map.render( buffer )
                    map.close()

                    feature.set([
                        
                        geom: new Point(minx, miny)
                        
                        ])
                    writer.add(feature)
                }
            }
        }
        layer
    }
}