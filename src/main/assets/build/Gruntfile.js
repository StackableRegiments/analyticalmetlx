module.exports = function( grunt ) {
    // Load grunt plugins
    grunt.loadNpmTasks('grunt-contrib-copy');
    grunt.loadNpmTasks('grunt-contrib-concat');
    grunt.loadNpmTasks('grunt-contrib-uglify');
    grunt.loadNpmTasks('grunt-contrib-less');

    grunt.initConfig({
        lint: {
            all: ['../js/*.js', '../js/vendor/*.js']
        },
        copy: {
            main: {
              files: [
                // includes files within path and its sub-directories
                  //{expand:true, flatten:true, src: ['../minimal-brand/source/styles/images/metl/**'], dest: '../../static/assets/styles/images/'},
                  //{expand:true, flatten:true, src: ['../minimal-brand/source/styles/images/all/all-search**'], dest: '../../static/assets/styles/images/'},
                //{src: ['../minimal-brand/source/js/vendor/jquery-1.11.1.min.js'], dest: '../../static/assets/js/vendor/jquery.min.js'}
              ]
            }
          },
        concat: {
            options: {
              separator: ';',
            },
            dist: {
              src: [
                    '../js/vendor/modernizr.2.8.3.min.js'
                    ],
              dest: 'source/js/minimal-core.js'
            },
        },
        
        uglify: {
            options: {
                mangle: false
            },
            my_target: {
                files: {
                  '../../webapp/static/assets/js/minimal-core.min.js': ['source/js/minimal-core.js']
                }
              }
            },

        less: {
            development: {
            options: {
              compress: true,
              yuicompress: true,
              optimization: 2
            },
            files: {
              // target.css file: source.less file
              "../../webapp/static/assets/styles/main.css": "../main.less"
            }
          }
        }
        
        
    });

    // Default build
    grunt.registerTask('default', [ 'copy', 'concat', 'uglify', 'less']);
};
