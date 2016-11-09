module.exports = function( grunt ) {
    // Load grunt plugins
    grunt.config('theme', grunt.option('theme') || process.env.GRUNT_ENV || 'neutral');

    grunt.loadNpmTasks('grunt-contrib-copy');
    grunt.loadNpmTasks('grunt-contrib-concat');
    grunt.loadNpmTasks('grunt-contrib-uglify');
    grunt.loadNpmTasks('grunt-contrib-less');
    grunt.loadNpmTasks('grunt-closure-tools');

    var config = {
        lint: {
            all: ['src/main/assets/js/*.js', 'src/main/assets/js/vendor/*.js']
        },
        copy: {
            main: {
                files: [
                    // includes files within path and its sub-directories
                    {expand:true, flatten:true, cwd: 'src/main/assets/styles/images',  src: ['**'], dest: 'src/main/webapp/static/assets/styles/images/'},
                    {src: ['src/main/assets/js/vendor/jquery-1.11.3.min.js'], dest: 'src/main/webapp/static/assets/js/vendor/jquery.min.js'}
                ]
            }
        },
        concat: {
            options: {
                separator: ';',
            },
            dist: {
                src: [
                    'src/main/assets/js/vendor/modernizr.2.8.3.min.js',
                    'src/main/assets/js/mod-popout.js'
                ],
                dest: 'src/main/assets/build/source/js/metl-front-end-core.js'
            },
        },

        uglify: {
            options: {
                mangle: false
            },
            my_target: {
                files: {
                    'src/main/webapp/static/assets/js/metl-front-end-core.min.js': ['src/main/assets/build/source/js/metl-front-end-core.js']
                }
            }
        },

        less: {
            development: {
                options: {
                    compress: true,
                    yuicompress: true,
                    optimization: 2,
                    modifyVars:{
                        theme: grunt.config('theme')
                    }
                },
                files: {}
            }
        },

        closureCompiler:  {
            options: {
                compilerFile: 'tools/closure-compiler-v20160713.jar',
                checkModified: true,
                d32: false, // will use 'java -client -d32 -jar compiler.jar'
                TieredCompilation: true, // will use 'java -server -XX:+TieredCompilation -jar compiler.jar'
                compilerOpts: {
                    create_source_map: null
                }
            },
            minify: {
                files: [
                    {
                        expand: true,
                        src: ['src/main/webapp/static/js/*.js', '!**/*.min.js', '!**/*~'],
                        dest: 'src/main/webapp/static/js/min',
                        ext: '.min.js'
                    }
                ]
            }
        }
    }

    config.less.development.files["src/main/webapp/static/assets/styles/"+grunt.config('theme')+"/main.css"] = "src/main/assets/main.less";
    grunt.initConfig(config);

    // Default build
    grunt.registerTask('default', [ 'copy', 'concat', 'uglify', 'less', 'closureCompiler']);
};
