import React from 'react'
import { shallow } from 'enzyme'
import sinon from 'sinon'

import Header from 'metabase/components/Header'
import EditBar from 'metabase/components/EditBar'
import TitleAndDescription from 'metabase/components/TitleAndDescription'
import Input from 'metabase/components/Input'

describe('Header', () => {
    it('should render an edit bar when needed', () => {
        const wrapper = shallow(
            <Header
                item={{ name: 'Test', description: 'test'}}
                isEditing={false}
            />
        )

        expect(wrapper.find(EditBar).length).toEqual(0)
        wrapper.setProps({ isEditing: true })
        expect(wrapper.find(EditBar).length).toEqual(1)
    })

    describe('title and description', () => {
        it('should render a title and description', () => {
            const wrapper = shallow(
                <Header
                    item={{ name: 'Test', description: 'test'}}
                    isEditing={false}
                />
            )
            expect(wrapper.find(TitleAndDescription).length).toEqual(1)
        })
        describe('an existing entity', () => {
            it('should pass a new title to title and dresciption if the object is not saved', () => {
                const wrapper = shallow(
                    <Header
                        item={{ name: 'test', description: 'test'}}
                        isediting={false}
                        objectType="question"
                    />
                )
                expect(wrapper.find(TitleAndDescription).props().title).toEqual('New question')

            })

            it('should pass the item title if the object exists', () => {
                const wrapper = shallow(
                    <Header
                        item={{id: 1, name: 'test', description: 'test'}}
                        isediting={false}
                        objectType="question"
                    />
                )
                const props = wrapper.find(TitleAndDescription).props()
                expect(props.title).toEqual('test')
                expect(props.description).toEqual('test')

            })
        })
        describe('editing', () => {
            it('should render two input elements with proper values', () => {
                const wrapper = shallow(
                    <Header
                        item={{id: 1, name: 'test', description: 'des'}}
                        isEditingInfo={true}
                    />
                )
                expect(wrapper.find(Input).length).toEqual(2)
                expect(wrapper.find(Input).get(0).props.value).toEqual('test')
                expect(wrapper.find(Input).get(1).props.value).toEqual('des')
            })

            it('should call the proper update function', () => {
                const setAttributeSpy = sinon.spy()

                const wrapper = shallow(
                    <Header
                        item={{id: 1, name: 'test', description: 'des'}}
                        isEditingInfo={true}
                        setItemAttributeFn={setAttributeSpy}
                    />
                )
                wrapper.find(Input).at(0).simulate(
                    'change',
                    { target: { value: 'hey' }
                })

                expect(setAttributeSpy.called).toEqual(true)
            })
        })
    })
    describe('header buttons', () => {
        it('should render the buttons provided', () => {
            const wrapper = shallow(
                <Header
                    item={{ id: 4 }}
                    headerButtons={[
                        <div>one</div>,
                        <div>two</div>
                    ]}
                />
            )
            expect(wrapper.find('ol').children().length).toEqual(2)
        })
    })
})
