<schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
    <ns uri="http://www.clarin.eu/cmd/" prefix="cmd"/>
    
    <pattern>
        <rule context="cmd:Header" role="warning">
            <assert test="string-length(cmd:MdProfile/text()) &gt; 0">
                A CMDI instance should contain a non-empty &lt;cmd:MdProfile&gt; element in &lt;cmd:Header&gt;.
            </assert>
        </rule>   
    </pattern>

    <!--
    <pattern>
        <rule context="cmd:Header" role="information">
            <report test="cmd:MdSelfLink">MdSelfLink "<value-of select="cmd:MdSelfLink"/>".</report>
        </rule>
    </pattern>
    -->
</schema>