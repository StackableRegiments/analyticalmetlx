module.exports = function( grunt ) {
    // Load grunt plugins
    grunt.config('theme', grunt.option('theme') || process.env.GRUNT_ENV || 'neutral');

    grunt.loadNpmTasks('grunt-contrib-copy');
    grunt.loadNpmTasks('grunt-contrib-concat');
    grunt.loadNpmTasks('grunt-contrib-uglify');
    grunt.loadNpmTasks('grunt-contrib-less');

    var config = {
        lint: {
            all: ['../js/*.js', '../js/vendor/*.js']
        },
        copy: {
            main: {
                files: [
                    // includes files within path and its sub-directories
                    {expand:true, flatten:true, cwd: '../styles/images',  src: ['**'], dest: '../../webapp/static/assets/styles/images/'},
                    {src: ['../js/vendor/jquery-1.11.3.min.js'], dest: '../../webapp/static/assets/js/vendor/jquery.min.js'}
                ]
            }
        },
        concat: {
            options: {
                separator: ';',
            },
            dist: {
                src: [
                    '../js/vendor/modernizr.2.8.3.min.js',
                    '../js/mod-popout.js'
                ],
                dest: 'source/js/metl-front-end-core.js'
            },
        },

        uglify: {
            options: {
                mangle: false
            },
            my_target: {
                files: {
                    '../../webapp/static/assets/js/metl-front-end-core.min.js': ['source/js/metl-front-end-core.js']
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
        }
    }
    config.less.development.files["../../webapp/static/assets/styles/"+grunt.config('theme')+"/main.css"] = "../main.less";
    grunt.initConfig(config);

    // Default build
    grunt.registerTask('default', [ 'copy', 'concat', 'uglify', 'less']);
};
