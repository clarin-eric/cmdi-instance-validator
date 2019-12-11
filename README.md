# CMDI Instance Validator

Library and tool to validate [CMDI](https://www.clarin.eu/cmdi) records.

For validation of CMDI component specifcations, see [CMDValidate](https://github.com/clarin-eric/cmd-validate).

[CMDI](https://www.clarin.eu/cmdi) instance validator version 1.2.1

## Using the CMDI Instance Validator tool

We recommend downloading the [latest release](https://github.com/clarin-eric/cmdi-instance-validator/releases) of the validator. It contains a script that wraps the tool and library and can be executed on the command line:

```
usage: cmdi-validator [-c <DIRECTORY>] [-d | -D | -q]  [-E] [-F
       <WILDCARD>] [-p | -P]   [-S | -s <FILE>]  [-t <COUNT> | -T]  [-v]
 -c,--schema-cache-dir <DIRECTORY>   schema caching directory
 -d,--debug                          enable debugging output
 -D,--trace                          enable full debugging output
 -E,--no-estimate                    disable gathering of total file count
                                     for progress reporting
 -F,--file-filter <WILDCARD>         only process filenames matching a
                                     wildcard
 -p,--check-pids                     check persistent identifiers syntax
 -P,--check-and-resolve-pids         check persistent identifiers syntax
                                     and if they resolve properly
 -q,--quiet                          be quiet
 -S,--no-schematron                  disable Schematron validator
 -s,--schematron-file <FILE>         load Schematron schema from file
 -t,--threads <COUNT>                number of validator threads
 -T,--no-threads                     disable threading
 -v,--verbose                        be verbose
```

## Development
The master branch is stable, please use `develop` for development (or fork and make pull requests to that branch).
