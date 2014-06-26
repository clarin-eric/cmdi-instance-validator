declare namespace svrl = "http://purl.oclc.org/dsdl/svrl";

for $node in //(svrl:failed-assert|svrl:successful-report)
let $role := data($node/preceding-sibling::svrl:fired-rule[1]/@role)
let $s :=
  if (empty($role)) then
    if (local-name($node) eq 'successful-report') then
      'I'
    else
      'E'
  else
    if (fn:starts-with($role, 'info')) then 'I'
    else if (fn:starts-with($role, 'warn')) then 'W'
    else 'E'
let $l := normalize-space(data($node/@location))
let $m := normalize-space(data($node/svrl:text/text()))
return <m s="{$s}" l="{$l}">{$m}</m>
